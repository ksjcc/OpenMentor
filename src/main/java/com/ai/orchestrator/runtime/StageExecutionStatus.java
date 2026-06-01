package com.ai.orchestrator.runtime;

public enum StageExecutionStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED,
  FALLBACK,
  SKIPPED
}
