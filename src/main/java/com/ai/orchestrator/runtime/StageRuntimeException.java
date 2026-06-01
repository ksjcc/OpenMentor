package com.ai.orchestrator.runtime;

public class StageRuntimeException extends RuntimeException {
  private final PipelineStageName stage;

  public StageRuntimeException(PipelineStageName stage, String message, Throwable cause) {
    super(message, cause);
    this.stage = stage;
  }

  public PipelineStageName getStage() {
    return stage;
  }
}
