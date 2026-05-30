package com.ai.orchestrator.pipeline;

import com.ai.agent.ScoreAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.entity.Trace.TraceEvent;
import com.service.JsonParseService;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoringStage {
  private final ScoreAgent scoreAgent;
  private final PromptBuilder promptBuilder;
  private final JsonParseService jsonParseService;

  public void execute(InterviewPipelineContext context) {
    long start = System.currentTimeMillis();
    String stageInput = (context.getQuestion() == null ? "" : context.getQuestion())
        + "\n"
        + (context.getAnswer() == null ? "" : context.getAnswer());
    PromptContext scoreContext = PromptContext.builder()
        .currentQuestion(context.getQuestion())
        .currentAnswer(context.getAnswer())
        .topic(context.getTopic())
        .build();

    String scoreJson = scoreAgent.score(promptBuilder.buildScorePrompt(scoreContext));
    context.setScoreJson(scoreJson);
    context.setScoreResult(jsonParseService.parseScore(scoreJson));

    if (context.getTraceContext() == null) {
      return;
    }

    long end = System.currentTimeMillis();
    TraceEvent event = TraceEvent.builder()
        .stage("scoring")
        .startTime(start)
        .endTime(end)
        .latencyMs(end - start)
        .input(stageInput)
        .output(scoreJson)
        .metadata(Map.of(
            "topic", context.getTopic() == null ? "" : context.getTopic(),
            "questionLength", context.getQuestion() == null ? 0 : context.getQuestion().length(),
            "answerLength", context.getAnswer() == null ? 0 : context.getAnswer().length(),
            "scoreJsonLength", scoreJson == null ? 0 : scoreJson.length(),
            "hasScoreResult", context.getScoreResult() != null))
        .build();

    context.getTraceContext()
        .addEvent(event);
  }
}
