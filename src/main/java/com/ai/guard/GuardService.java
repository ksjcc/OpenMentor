package com.ai.guard;

public interface GuardService {
  <T> T validate(
      String rawOutput,
      Class<T> clazz);
}
