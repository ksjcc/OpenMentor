package com.service.garphrag;

import lombok.Data;

@Data
public class RetrievalBudget {
  private int remainingRetrieverCalls;

  private int remainingGraphHop;

  private int remainingChunks;

  private int remainingLlmCalls;

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
}
