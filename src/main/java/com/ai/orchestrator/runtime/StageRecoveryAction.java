package com.ai.orchestrator.runtime;

public enum StageRecoveryAction {
  NONE,
  RETRY,
  FALLBACK,
  SKIP,
  DEFAULT_VALUE
}
