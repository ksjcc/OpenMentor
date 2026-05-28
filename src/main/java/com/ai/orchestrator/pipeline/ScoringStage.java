package com.ai.orchestrator.pipeline;

import com.ai.agent.ScoreAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.service.JsonParseService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoringStage {
  private final ScoreAgent scoreAgent;
  private final PromptBuilder promptBuilder;
  private final JsonParseService jsonParseService;

  public void execute(InterviewPipelineContext context) {
    PromptContext scoreContext = PromptContext.builder()
        .currentQuestion(context.getQuestion())
        .currentAnswer(context.getAnswer())
        .topic(context.getTopic())
        .build();

    String scoreJson = scoreAgent.score(promptBuilder.buildScorePrompt(scoreContext));
    context.setScoreJson(scoreJson);
    context.setScoreResult(jsonParseService.parseScore(scoreJson));
  }
}
