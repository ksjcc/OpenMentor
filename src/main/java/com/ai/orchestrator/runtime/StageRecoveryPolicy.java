package com.ai.orchestrator.runtime;

import com.ai.orchestrator.pipeline.InterviewPipelineContext;

@FunctionalInterface
public interface StageRecoveryPolicy {
  StageRecoveryResult recover(InterviewPipelineContext context, Exception exception);
}
