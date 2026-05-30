package com.ai.orchestrator.pipeline;

import java.util.Map;

import com.ai.prompt.PromptContext;
import com.dto.decision.FinalDecisionDTO;
import com.entity.Trace.TraceEvent;
import com.service.cache.InterviewMemoryCacheService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptAssemblyStage {
  private final InterviewMemoryCacheService interviewMemoryCacheService;

  public void execute(InterviewPipelineContext context,
      String profileJson,
      String summaryMd,
      FinalDecisionDTO finalDecision) {
    long start = System.currentTimeMillis();
    String recentContext = context.getSessionId() == null
        ? "暂无短期上下文。"
        : interviewMemoryCacheService.buildRecentContext(context.getSessionId());

    PromptContext promptContext = PromptContext.builder()
        .userId(context.getUserId())
        .sessionId(context.getSessionId())
        .profileJson(profileJson)
        .summaryMd(summaryMd)
        .ragMemories(context.getGraphRagContext())
        .recentContext(recentContext)
        .currentQuestion(context.getQuestion())
        .currentAnswer(context.getAnswer())
        .scoreJson(context.getScoreJson())
        .topic(finalDecision.getTopic() == null ? context.getTopic() : finalDecision.getTopic())
        .graphReasoning(finalDecision.getReason())
        .build();

    context.setPromptContext(promptContext);

    if (context.getTraceContext() == null) {
      return;
    }

    long end = System.currentTimeMillis();
    TraceEvent event = TraceEvent.builder()
        .stage("prompt_assembly")
        .startTime(start)
        .endTime(end)
        .latencyMs(end - start)
        .input(context.getQuestion() + "\n" + context.getAnswer())
        .output(finalDecision.getAction().name())
        .metadata(Map.of(
            "action", finalDecision.getAction().name(),
            "topic", promptContext.getTopic() == null ? "" : promptContext.getTopic(),
            "recentContextLength", recentContext == null ? 0 : recentContext.length(),
            "ragContextCount", promptContext.getRagMemories() == null ? 0 : promptContext.getRagMemories().size(),
            "hasProfile", profileJson != null && !profileJson.isBlank(),
            "hasSummary", summaryMd != null && !summaryMd.isBlank(),
            "graphMode", context.getUsedGraphMode() == null ? "" : context.getUsedGraphMode().name(),
            "fallbackTriggered", context.isGraphFallbackTriggered(),
            "decisionReason", finalDecision.getReason() == null ? "" : finalDecision.getReason()))
        .build();

    context.getTraceContext()
        .addEvent(event);
  }

}
