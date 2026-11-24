package com.ai.harnessService;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import com.ai.resilience.InterviewFallbackService;
import com.ai.resilience.InterviewFallbackService.FallbackReply;
import com.ai.resilience.InterviewFallbackService.Reason;
import com.ai.resilience.LlmCircuitBreaker;
import com.ai.resilience.LlmConcurrencyGuard;
import com.dto.score.ScoreResultDTO;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewHarnessService {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);
  private static final String OPENING_ANSWER = "这是本场面试的第一轮，请直接出第一道开场题。";

  /** 简历正文注入上限：够模型抓住项目细节，又不至于把上下文吃光。 */
  private static final int RESUME_CONTEXT_MAX_CHARS = 1200;
  private static final int PROJECT_SUMMARY_MAX_CHARS = 800;

  private final HarnessAgent interviewerAgent;
  private final KnowledgeExtractorAgent knowledgeExtractorAgent;
  private final PromptBuilder promptBuilder;

  private final LlmConcurrencyGuard concurrencyGuard;
  private final LlmCircuitBreaker circuitBreaker;
  private final InterviewFallbackService fallbackService;

  /**
   * 本场面试的设定：面试类型 / 目标岗位 / 主题 / 简历。
   *
   * <p>
   * 这些内容每次调用都会以「本场面试设定」的形式拼进用户消息，所以模型每一轮
   * 都能看到候选人简历和目标岗位，而不是只靠系统提示词里的通用人格。
   */
  public record CandidateContext(
      String interviewType,
      String targetRole,
      String topic,
      String resumeTitle,
      String resumeText,
      String projectSummary) {

    public static CandidateContext ofTopic(String topic) {
      return new CandidateContext(null, null, topic, null, null, null);
    }
  }

  /** @param degraded true 表示这轮是降级回复，不是模型输出 */
  public record TurnOutcome(String action, String content, String scoreObservation,
      ScoreResultDTO scoreResult, boolean degraded) {

    public static TurnOutcome normal(String action, String content, String scoreObservation,
        ScoreResultDTO scoreResult) {
      return new TurnOutcome(action, content, scoreObservation, scoreResult, false);
    }

    public static TurnOutcome degraded(String action, String content, String scoreJson) {
      return new TurnOutcome(action, content, scoreJson, null, true);
    }

    /** 本轮总分；这轮没有调用评分工具时为 null。 */
    public Integer score() {
      return scoreResult == null ? null : scoreResult.getScore();
    }
  }

  public TurnOutcome ask(Long userId, Long sessionId, String topic, String question, String answer, int turnIndex) {
    return ask(userId, sessionId, CandidateContext.ofTopic(topic), question, answer, turnIndex);
  }

  public TurnOutcome ask(Long userId, Long sessionId, CandidateContext context, String question, String answer,
      int turnIndex) {
    RuntimeContext ctx = buildContext(userId, sessionId, context, question, answer, turnIndex);

    // L2 全局并发闸
    if (!concurrencyGuard.tryAcquire()) {
      circuitBreaker.onAborted();
      return degrade(Reason.BUSY, ctx, turnIndex);
    }
    // L4 熔断
    if (!circuitBreaker.tryAcquire()) {
      concurrencyGuard.release();
      return degrade(Reason.BREAKER_OPEN, ctx, turnIndex);
    }

    long start = System.currentTimeMillis();
    try {
      Msg reply = interviewerAgent.call(List.of(buildInput(answer, ctx)), ctx).block(DEFAULT_TIMEOUT);
      circuitBreaker.onSuccess();
      log.info("harness turn done sessionId={} turn={} cost={}ms breaker={}",
          sessionId, turnIndex, System.currentTimeMillis() - start, circuitBreaker.state());

      String content = reply == null ? null : reply.getTextContent();
      if (content == null || content.isBlank()) {
        return degrade(Reason.MODEL_ERROR, ctx, turnIndex);
      }
      return TurnOutcome.normal(
          asString(ctx.get("turnAction"), "NEXT"),
          content,
          asString(ctx.get("score.observation"), null),
          extractScoreResult(ctx));
    } catch (Exception ex) {
      Reason reason = classify(ex);
      // 过载不算模型故障，不能推进熔断计数
      if (reason == Reason.BUSY) {
        circuitBreaker.onAborted();
      } else {
        circuitBreaker.onFailure();
      }
      log.warn("harness turn failed sessionId={} turn={} reason={} breaker={}",
          sessionId, turnIndex, reason, circuitBreaker.summary(), ex);
      return degrade(reason, ctx, turnIndex);
    } finally {
      concurrencyGuard.release();
    }
  }

  public Disposable askStreaming(
      Long userId, Long sessionId, CandidateContext context, String question, String answer, int turnIndex,
      java.util.function.Consumer<String> onToken,
      java.util.function.Consumer<TurnOutcome> onDone,
      java.util.function.Consumer<Throwable> onError) {

    RuntimeContext ctx = buildContext(userId, sessionId, context, question, answer, turnIndex);

    if (!concurrencyGuard.tryAcquire()) {
      onDone.accept(TurnOutcome.degraded("CLARIFY",
          fallbackService.build(Reason.BUSY, null, turnIndex).content(), null));
      return null;
    }
    if (!circuitBreaker.tryAcquire()) {
      concurrencyGuard.release();
      onDone.accept(TurnOutcome.degraded("CLARIFY",
          fallbackService.build(Reason.BREAKER_OPEN, null, turnIndex).content(), null));
      return null;
    }

    final StringBuilder buffer = new StringBuilder();
    return interviewerAgent.stream(List.of(buildInput(answer, ctx)), ctx)
        .filter(event -> event.getType() == EventType.AGENT_RESULT)
        .map(Event::getMessage)
        .filter(msg -> msg.getTextContent() != null && !msg.getTextContent().isBlank())
        .doOnNext(msg -> {
          buffer.append(msg.getTextContent());
          onToken.accept(msg.getTextContent());
        })
        .doOnComplete(() -> {
          circuitBreaker.onSuccess();
          concurrencyGuard.release();
          onDone.accept(TurnOutcome.normal(
              asString(ctx.get("turnAction"), "NEXT"),
              buffer.toString(),
              asString(ctx.get("score.observation"), null),
              extractScoreResult(ctx)));
        })
        .doOnError(error -> {
          circuitBreaker.onFailure();
          concurrencyGuard.release();
          // 已经吐出过内容就保留，不要再用降级话术覆盖
          if (buffer.length() > 0) {
            onDone.accept(TurnOutcome.degraded(
                asString(ctx.get("turnAction"), "NEXT"), buffer.toString(), null));
          } else {
            onDone.accept(TurnOutcome.degraded("CLARIFY",
                fallbackService.build(classify(error), null, turnIndex).content(), null));
          }
          onError.accept(error);
        })
        .subscribe();
  }

  public String extractKnowledge(Long userId, Long sessionId, String transcript) {
    String prompt = promptBuilder.buildKnowledgeExtractionPrompt(PromptContext.builder()
        .userId(userId)
        .sessionId(sessionId)
        .dialogue(transcript)
        .task(PromptTemplates.TASK_EXTRACTION)
        .build());
    try {
      return knowledgeExtractorAgent.extract(prompt);
    } catch (Exception ex) {
      log.warn("知识提炼失败 sessionId={}", sessionId, ex);
      return null;
    }
  }

  // ==================== 内部 ====================

  private TurnOutcome degrade(Reason reason, RuntimeContext ctx, int turnIndex) {
    String weakTopic = asString(ctx.get("score.observation"), null);
    FallbackReply reply = fallbackService.build(reason, weakTopic, turnIndex);
    return TurnOutcome.degraded(reply.action(), reply.content(), null);
  }

  private ScoreResultDTO extractScoreResult(RuntimeContext ctx) {
    Object value = ctx.get("scoreResult");
    return value instanceof ScoreResultDTO scoreResult ? scoreResult : null;
  }

  private Reason classify(Throwable error) {
    if (error == null) {
      return Reason.UNKNOWN;
    }
    if (error instanceof com.ai.resilience.OverloadedException) {
      return Reason.BUSY;
    }
    Throwable cause = error.getCause();
    String text = (String.valueOf(error.getMessage()) + " " + String.valueOf(cause)).toLowerCase();
    if (text.contains("timeout") || text.contains("超时")) {
      return Reason.TIMEOUT;
    }
    return Reason.MODEL_ERROR;
  }

  /**
   * 拼「本场面试设定 + 本轮输入」。
   *
   * <p>
   * 背景每轮都带：harness 不保证跨轮记忆，模型能稳定看到简历和岗位，
   * 追问才不会飘回通用八股。
   */
  private Msg buildInput(String answer, RuntimeContext ctx) {
    String body = answer == null || answer.isBlank() ? OPENING_ANSWER : answer;
    return Msg.builderForRole(MsgRole.USER)
        .textContent(renderSettingBlock(ctx) + "\n【本轮输入】\n" + body)
        .build();
  }

  private String renderSettingBlock(RuntimeContext ctx) {
    StringBuilder block = new StringBuilder("【本场面试设定】\n");
    appendLine(block, "面试类型", describeInterviewType(asString(ctx.get("interviewType"), null)));
    appendLine(block, "目标岗位", asString(ctx.get("targetRole"), null));
    appendLine(block, "面试主题", asString(ctx.get("topic"), null));

    String resumeTitle = asString(ctx.get("resumeTitle"), null);
    String resumeText = asString(ctx.get("resumeText"), null);
    if (resumeText != null && !resumeText.isBlank()) {
      block.append("- 候选人简历");
      if (resumeTitle != null && !resumeTitle.isBlank()) {
        block.append("（").append(resumeTitle).append("）");
      }
      block.append("：\n");
      block.append(truncate(resumeText, RESUME_CONTEXT_MAX_CHARS)).append('\n');
    } else {
      block.append("- 候选人简历：本次未提供，按标准技术面试进行\n");
    }

    String projectSummary = asString(ctx.get("projectSummary"), null);
    if (projectSummary != null && !projectSummary.isBlank()) {
      block.append("- 候选人补充的项目摘要：\n")
          .append(truncate(projectSummary, PROJECT_SUMMARY_MAX_CHARS)).append('\n');
    }
    return block.toString();
  }

  private void appendLine(StringBuilder block, String label, String value) {
    if (value != null && !value.isBlank()) {
      block.append("- ").append(label).append("：").append(value.trim()).append('\n');
    }
  }

  private String describeInterviewType(String interviewType) {
    if (interviewType == null || interviewType.isBlank()) {
      return null;
    }
    return switch (interviewType.trim().toUpperCase()) {
      case "TECH" -> "技术面试（技术）";
      case "PROJECT" -> "项目面试（PROJECT）";
      case "HR" -> "HR 面试（HR）";
      default -> interviewType.trim();
    };
  }

  private String truncate(String text, int max) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.length() > max ? trimmed.substring(0, max) + "……" : trimmed;
  }

  private RuntimeContext buildContext(
      Long userId, Long sessionId, CandidateContext context, String question, String answer, int turnIndex) {
    CandidateContext safeContext = context == null ? CandidateContext.ofTopic(null) : context;
    String sid = sessionId == null ? "ephemeral-" + java.util.UUID.randomUUID() : String.valueOf(sessionId);
    return RuntimeContext.builder()
        .userId(String.valueOf(userId))
        .sessionId(sid)
        .put("topic", nullToEmpty(safeContext.topic()))
        .put("interviewType", nullToEmpty(safeContext.interviewType()))
        .put("targetRole", nullToEmpty(safeContext.targetRole()))
        .put("resumeTitle", nullToEmpty(safeContext.resumeTitle()))
        .put("resumeText", nullToEmpty(safeContext.resumeText()))
        .put("projectSummary", nullToEmpty(safeContext.projectSummary()))
        .put("currentQuestion", nullToEmpty(question))
        .put("currentAnswer", nullToEmpty(answer))
        .put("turnIndex", turnIndex)
        .build();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String asString(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value);
  }
}
