package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ai.memory.MemoryRetriever;
import com.common.GraphRagRetrieverType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GraphRagMemorySearchStrategy implements GraphRagSearchStrategy {
  private final MemoryRetriever memoryRetriever;

  @Override
  public GraphRagRetrieverType type() {
    return GraphRagRetrieverType.MEMORY;
  }

  @Override
  public List<String> search(Long userId, String query, RetrievalBudget budget) {
    if (budget != null && !budget.consumeGraphHop()) {
      return List.of();
    }
    List<String> results = memoryRetriever.retrieveByUserId(userId);
    if (budget == null) {
      return results;
    }
    return results.stream()
        .filter(item -> budget.consumeChunk(1))
        .toList();
  }
}
