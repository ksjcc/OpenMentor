package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ai.rag.VectorStoreService;
import com.common.GraphRagRetrieverType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GraphRagVectorSearchStrategy implements GraphRagSearchStrategy {
  private final VectorStoreService vectorStoreService;

  @Override
  public GraphRagRetrieverType type() {
    return GraphRagRetrieverType.VECTOR;
  }

  @Override
  public List<String> search(Long userId, String query, RetrievalBudget budget) {
    if (budget != null && !budget.consumeGraphHop()) {
      return List.of();
    }
    List<String> results = vectorStoreService.search(userId, query, 6);
    if (budget == null) {
      return results;
    }
    return results.stream()
        .filter(item -> budget.consumeChunk(1))
        .toList();
  }
}
