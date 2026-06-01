package com.ai.orchestrator.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageExecutionState {
  private PipelineStageName stage;
  private StageExecutionStatus status;
  private StageRecoveryAction recoveryAction;
  private String failureReason;
  private String recoveryReason;
  private long startedAt;
  private long endedAt;
  private long latencyMs;

  public boolean isCompleted() {
    return status == StageExecutionStatus.SUCCESS
        || status == StageExecutionStatus.FALLBACK
        || status == StageExecutionStatus.SKIPPED;
  }
}
