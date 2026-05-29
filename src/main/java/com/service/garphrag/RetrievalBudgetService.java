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

    return budget;
  }
}
