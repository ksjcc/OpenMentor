package com.ai.orchestrator.runtime;

public record StageRecoveryResult(
    boolean rethrow,
    StageExecutionStatus status,
    StageRecoveryAction action,
    String failureReason,
    String recoveryReason) {

  public static StageRecoveryResult fail(Exception exception) {
    String reason = exception == null ? "unknown_failure" : exception.getMessage();
    return new StageRecoveryResult(
        true,
        StageExecutionStatus.FAILED,
        StageRecoveryAction.NONE,
        reason == null ? "unknown_failure" : reason,
        "");
  }

  public static StageRecoveryResult fallback(String failureReason, String recoveryReason) {
    return new StageRecoveryResult(
        false,
        StageExecutionStatus.FALLBACK,
        StageRecoveryAction.FALLBACK,
        failureReason,
        recoveryReason);
  }

  public static StageRecoveryResult skipped(String failureReason, String recoveryReason) {
    return new StageRecoveryResult(
        false,
        StageExecutionStatus.SKIPPED,
        StageRecoveryAction.SKIP,
        failureReason,
        recoveryReason);
  }

  public static StageRecoveryResult defaultValue(String failureReason, String recoveryReason) {
    return new StageRecoveryResult(
        false,
        StageExecutionStatus.FALLBACK,
        StageRecoveryAction.DEFAULT_VALUE,
        failureReason,
        recoveryReason);
  }
}
