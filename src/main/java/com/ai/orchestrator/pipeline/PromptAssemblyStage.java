package com.ai.orchestrator.pipeline;

import com.ai.prompt.PromptContext;
import com.dto.decision.FinalDecisionDTO;
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
  }

}
