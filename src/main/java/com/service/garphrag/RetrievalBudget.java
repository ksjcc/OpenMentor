package com.service.garphrag;

import lombok.Data;

@Data
public class RetrievalBudget {
  private int remainingRetrieverCalls;

  private int remainingGraphHop;

  private int remainingChunks;

  private int remainingLlmCalls;

  private int remainingSearchSteps;

  private long timeoutMs;

  private long startedAt;

  public void start() {
    if (startedAt <= 0) {
      startedAt = System.currentTimeMillis();
    }
  }

  public boolean consumeRetrieverCall() {
    if (remainingRetrieverCalls <= 0) {
      return false;
    }
    remainingRetrieverCalls--;
    return true;
  }

  public boolean consumeGraphHop() {
    if (remainingGraphHop <= 0) {
      return false;
    }
    remainingGraphHop--;
    return true;
  }

  public boolean consumeChunk(int count) {
    if (remainingChunks < count) {
      return false;
    }
    remainingChunks -= count;
    return true;
  }

  public boolean consumeLlmCall() {
    if (remainingLlmCalls <= 0) {
      return false;
    }
    remainingLlmCalls--;
    return true;
  }

  public boolean consumeSearchStep() {
    if (remainingSearchSteps <= 0) {
      return false;
    }
    remainingSearchSteps--;
    return true;
  }

  public boolean isTimedOut() {
    if (timeoutMs <= 0 || startedAt <= 0) {
      return false;
    }
    return System.currentTimeMillis() - startedAt > timeoutMs;
  }

  public void capSearchSteps(int maxSteps) {
    if (maxSteps <= 0) {
      remainingSearchSteps = 0;
      return;
    }
    if (remainingSearchSteps <= 0) {
      remainingSearchSteps = maxSteps;
      return;
    }
    remainingSearchSteps = Math.min(remainingSearchSteps, maxSteps);
  }

  public String summary() {
    return "calls=%d,hops=%d,chunks=%d,llm=%d,steps=%d,timeoutMs=%d".formatted(
        remainingRetrieverCalls,
        remainingGraphHop,
        remainingChunks,
        remainingLlmCalls,
        remainingSearchSteps,
        timeoutMs);
  }
}
