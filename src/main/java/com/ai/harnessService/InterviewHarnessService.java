package com.ai.harnessService;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * 面试 harness 的调用入口。
 *
 * <p>这里不含任何"下一步做什么"的逻辑：流程由 HarnessAgent 内部的 ReAct 循环决定，
 * 本类只负责 组装 RuntimeContext → 调用 → 取回结果。
 *
 * <p>隔离保证：每次调用都有独立的 RuntimeContext（userId + sessionId），
 * 工具只能通过它拿到数据，拿不到就报错，不会串到别的面试。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewHarnessService {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);
  private static final String OPENING_ANSWER = "这是本场面试的第一轮，请直接出第一道开场题。";

  private final HarnessAgent interviewerAgent;
  private final KnowledgeExtractorAgent knowledgeExtractorAgent;
  private final PromptBuilder promptBuilder;

  /**
   * 一轮问答的结果。
   *
   * @param action    本轮动作（FOLLOW_UP / NEXT / HARDER / FINISH / CLARIFY），由 mark_turn_action 工具写入
   * @param content   对候选人说的话
   * @param scoreJson 本轮评分的观测文本，没有评分时为 null
   */
  public record TurnOutcome(String action, String content, String scoreJson) {
  }

  /** 非流式：跑完一轮，取回动作 + 回复。 */
  public TurnOutcome ask(Long userId, Long sessionId, String topic, String question, String answer, int turnIndex) {
    RuntimeContext ctx = buildContext(userId, sessionId, topic, question, answer, turnIndex);
    Msg input = buildInput(answer);

    long start = System.currentTimeMillis();
    Msg reply = interviewerAgent.call(List.of(input), ctx).block(DEFAULT_TIMEOUT);
    log.info("harness turn done sessionId={} turn={} cost={}ms", sessionId, turnIndex,
        System.currentTimeMillis() - start);

    String action = asString(ctx.get("turnAction"), "NEXT");
    String content = reply == null ? null : reply.getTextContent();
    String scoreJson = asString(ctx.get("score.observation"), null);
    return new TurnOutcome(action, content, scoreJson);
  }

  /**
   * 流式：把最终回复推给 SSE。
   *
   * <p>EventType 有三类值得注意：REASONING（思考）/ TOOL_RESULT（工具结果）/
   * AGENT_RESULT（最终回复）。只有 AGENT_RESULT 是要给用户看的。
   */
  public Disposable askStreaming(
      Long userId,
      Long sessionId,
      String topic,
      String question,
      String answer,
      int turnIndex,
      java.util.function.Consumer<String> onToken,
      java.util.function.Consumer<TurnOutcome> onDone,
      java.util.function.Consumer<Throwable> onError) {

    RuntimeContext ctx = buildContext(userId, sessionId, topic, question, answer, turnIndex);

    return interviewerAgent.stream(List.of(buildInput(answer)), ctx)
        .filter(event -> event.getType() == EventType.AGENT_RESULT)
        .map(Event::getMessage)
        .filter(msg -> msg.getTextContent() != null && !msg.getTextContent().isBlank())
        .doOnNext(msg -> onToken.accept(msg.getTextContent()))
        .doOnComplete(() -> onDone.accept(new TurnOutcome(
            asString(ctx.get("turnAction"), "NEXT"),
            null,
            asString(ctx.get("score.observation"), null))))
        .doOnError(onError)
        .subscribe();
  }

  /**
   * 面试结束后的知识提炼（复盘）。
   *
   * <p>这里刻意<b>不</b>让主 agent 跑：复盘要的是严格 JSON，用专门的结构化抽取
   * agent 输出更稳。主 agent 的职责是对着候选人说话，不是产出结构化数据。
   */
  public String extractKnowledge(Long userId, Long sessionId, String transcript) {
    String prompt = promptBuilder.buildKnowledgeExtractionPrompt(PromptContext.builder()
        .userId(userId)
        .sessionId(sessionId)
        .dialogue(transcript)
        .task(PromptTemplates.TASK_EXTRACTION)
        .build());
    log.info("knowledge extraction start sessionId={} transcriptLen={}", sessionId,
        transcript == null ? 0 : transcript.length());
    return knowledgeExtractorAgent.extract(prompt);
  }

  private Msg buildInput(String answer) {
    String text = answer == null || answer.isBlank() ? OPENING_ANSWER : answer;
    return Msg.builderForRole(MsgRole.USER).textContent(text).build();
  }

  private RuntimeContext buildContext(
      Long userId, Long sessionId, String topic, String question, String answer, int turnIndex) {
    // sessionId 为空表示无会话的临时调用：给一个一次性 id，保证不会和任何真实面试串味
    String sid = sessionId == null ? "ephemeral-" + java.util.UUID.randomUUID() : String.valueOf(sessionId);
    return RuntimeContext.builder()
        .userId(String.valueOf(userId))
        .sessionId(sid)
        .put("topic", topic == null ? "" : topic)
        .put("currentQuestion", question == null ? "" : question)
        .put("currentAnswer", answer == null ? "" : answer)
        .put("turnIndex", turnIndex)
        .build();
  }

  private static String asString(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value);
  }
}
