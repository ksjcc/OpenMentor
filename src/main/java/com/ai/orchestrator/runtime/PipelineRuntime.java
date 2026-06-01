package com.ai.orchestrator.runtime;

import java.util.EnumMap;
import java.util.Map;

import lombok.Data;

@Data
public class PipelineRuntime {
  private final Map<PipelineStageName, StageExecutionState> stages =
      new EnumMap<>(PipelineStageName.class);

  public void initialize() {
    for (PipelineStageName stage : PipelineStageName.values()) {
      stages.put(stage, StageExecutionState.builder()
          .stage(stage)
          .status(StageExecutionStatus.PENDING)
          .recoveryAction(StageRecoveryAction.NONE)
          .build());
    }
  }

  public StageExecutionState get(PipelineStageName stage) {
    return stages.get(stage);
  }

  public void update(StageExecutionState state) {
    stages.put(state.getStage(), state);
  }

  public boolean canRun(PipelineStageName stage) {
    StageExecutionState state = stages.get(stage);
    return state == null || state.getStatus() == StageExecutionStatus.PENDING;
  }
}
