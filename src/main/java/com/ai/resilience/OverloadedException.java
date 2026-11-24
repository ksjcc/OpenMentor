package com.ai.resilience;

public class OverloadedException extends RuntimeException {
  public OverloadedException(String message) {
    super(message);
  }

}
