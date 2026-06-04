package com.ai.orchestrator.pipeline;

import java.util.Map;
import static java.util.Map.entry;

import com.ai.prompt.PromptActionType;
import com.ai.prompt.PromptBudgetAllocator;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import com.ai.runtime.PromptRuntimeMetrics;
import com.ai.runtime.PromptRuntimeTracker;
import com.dto.decision.FinalDecisionDTO;
import com.entity.Trace.TraceEvent;
import com.service.cache.InterviewMemoryCacheService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptAssemblyStage {
  private final InterviewMemoryCacheService interviewMemoryCacheService;
  private final PromptBudgetAllocator promptBudgetAllocator;

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
        .ragMemories(context.getRetrievalContext())
        .recentContext(recentContext)
        .currentQuestion(context.getQuestion())
        .currentAnswer(context.getAnswer())
        .scoreJson(context.getScoreJson())
        .topic(finalDecision.getTopic() == null ? context.getTopic() : finalDecision.getTopic())
        .targetRole(context.getTargetRole())
        .resumeText(context.getResumeText())
        .projectSummary(context.getProjectSummary())
        .task(resolveTask(finalDecision))
        .graphReasoning(finalDecision.getReason())
        .build();
    PromptActionType promptAction = PromptActionType.fromTask(promptContext.getTask());
    PromptContext compressed = promptBudgetAllocator.compress(
        promptContext,
        promptAction);
    PromptRuntimeTracker.recordCompression(promptContext, compressed, promptAction);
    PromptRuntimeMetrics runtimeMetrics = PromptRuntimeTracker.snapshot();

    context.setPromptContext(compressed);

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
        .metadata(Map.ofEntries(
            entry("action", finalDecision.getAction().name()),
            entry("topic", promptContext.getTopic() == null ? "" : promptContext.getTopic()),
            entry("recentContextLength", recentContext == null ? 0 : recentContext.length()),
            entry("ragContextCount", promptContext.getRagMemories() == null ? 0 : promptContext.getRagMemories().size()),
            entry("compressedRagContextCount",
                compressed.getRagMemories() == null ? 0 : compressed.getRagMemories().size()),
            entry("graphContextCount", context.getGraphRagContext() == null ? 0 : context.getGraphRagContext().size()),
            entry("rawContextChars", runtimeMetrics.getRawContextChars()),
            entry("compressedContextChars", runtimeMetrics.getCompressedContextChars()),
            entry("estimatedRawContextTokens", runtimeMetrics.getEstimatedRawContextTokens()),
            entry("estimatedCompressedContextTokens", runtimeMetrics.getEstimatedCompressedContextTokens()),
            entry("estimatedContextTokensSaved", runtimeMetrics.getEstimatedContextTokensSaved()),
            entry("compressionRatio", runtimeMetrics.getCompressionRatio()),
            entry("promptBudgetApplied", runtimeMetrics.isPromptBudgetApplied()),
            entry("tokenEstimationMethod", runtimeMetrics.getTokenEstimationMethod()),
            entry("hasProfile", profileJson != null && !profileJson.isBlank()),
            entry("hasSummary", summaryMd != null && !summaryMd.isBlank()),
            entry("promptTask", promptContext.getTask() == null ? "" : promptContext.getTask()),
            entry("graphMode", context.getUsedGraphMode() == null ? "" : context.getUsedGraphMode().name()),
            entry("fallbackTriggered", context.isGraphFallbackTriggered()),
            entry("decisionReason", finalDecision.getReason() == null ? "" : finalDecision.getReason())))
        .build();

    context.getTraceContext()
        .addEvent(event);
  }

  private String resolveTask(FinalDecisionDTO finalDecision) {
    if (finalDecision == null || finalDecision.getAction() == null) {
      return PromptTemplates.TASK_NEW_QUESTION;
    }

    return switch (finalDecision.getAction()) {
      case FOLLOW_UP -> PromptTemplates.TASK_FOLLOW_UP;
      case HARDER -> PromptTemplates.TASK_HARDER;
      case NEXT -> PromptTemplates.TASK_NEW_QUESTION;
      case FINISH -> PromptTemplates.TASK_DECISION;
    };
  }
}
