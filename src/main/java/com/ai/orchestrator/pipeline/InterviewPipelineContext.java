package com.ai.orchestrator.pipeline;

import java.util.List;

import com.ai.prompt.PromptContext;
import com.dto.score.ScoreResultDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InterviewPipelineContext {
  private Long userId;
  private Long sessionId;
  private String topic;
  private String question;
  private String answer;
  private boolean realTime;

  private String scoreJson;
  private ScoreResultDTO scoreResult;
  private String weakTopic;
  private List<String> graphRagContext;
  private PromptContext promptContext;
}
