package com.service.garphrag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RetrievalBudgetService {
  @Value("${graphrag.budget.retriever-calls:3}")
  private int maxRetrieverCalls;

  @Value("${graphrag.budget.graph-hop:2}")
  private int maxGraphHop;

  @Value("${graphrag.budget.chunk-limit:12}")
  private int maxChunks;

  @Value("${graphrag.budget.llm-calls:2}")
  private int maxLlmCalls;

  @Value("${graphrag.budget.search-steps:6}")
  private int maxSearchSteps;

  @Value("${graphrag.budget.timeout-ms:2500}")
  private long timeoutMs;

  public RetrievalBudget createBudget() {

    RetrievalBudget budget = new RetrievalBudget();

    budget.setRemainingRetrieverCalls(
        maxRetrieverCalls);

    budget.setRemainingGraphHop(
        maxGraphHop);

    budget.setRemainingChunks(
        maxChunks);

    budget.setRemainingLlmCalls(
        maxLlmCalls);

    budget.setRemainingSearchSteps(
        maxSearchSteps);

    budget.setTimeoutMs(
        timeoutMs);

    budget.start();

    return budget;
  }
}
