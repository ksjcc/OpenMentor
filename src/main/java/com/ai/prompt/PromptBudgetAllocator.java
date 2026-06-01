package com.ai.prompt;

public interface PromptBudgetAllocator {
  PromptContext compress(
      PromptContext context,
      PromptActionType action);
}
