package com.ai.orchestrator.pipeline;

import java.util.List;

import com.ai.decision.RetrievalSlottingDecision;
import com.common.GraphRagMode;
import com.ai.orchestrator.runtime.PipelineRuntime;
import com.ai.prompt.PromptContext;
import com.dto.Retrieval.RetrievalPlanDTO;
import com.dto.score.ScoreResultDTO;
import com.service.garphrag.RetrievalBudget;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InterviewPipelineContext {
  private Long userId;
  private Long sessionId;
  private String topic;
  private String targetRole;
  private String resumeText;
  private String projectSummary;
  private String question;
  private String answer;
  private boolean realTime;

  private String scoreJson;
  private ScoreResultDTO scoreResult;
  private String weakTopic;
  private List<String> retrievalContext;
  private List<String> graphRagContext;
  private GraphRagMode usedGraphMode;
  private boolean graphFallbackTriggered;
  private PromptContext promptContext;
  private RetrievalPlanDTO retrievalPlan;
  private RetrievalSlottingDecision retrievalSlottingDecision;
  private RetrievalBudget retrievalBudget;
  private PipelineRuntime pipelineRuntime;
  private TraceContext traceContext;
}
