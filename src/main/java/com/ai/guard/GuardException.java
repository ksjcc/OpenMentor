package com.ai.guard;

public class GuardException extends RuntimeException {

  public GuardException(
      String message,
      Throwable cause) {

    super(message, cause);
  }
}
