package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.common.GraphRagRetrieverType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GraphRagGlobalSearchStrategy implements GraphRagSearchStrategy {
  private final GraphRagGlobalSearchService globalSearchService;

  @Override
  public GraphRagRetrieverType type() {
    return GraphRagRetrieverType.GLOBAL;
  }

  @Override
  public List<String> search(Long userId, String query, RetrievalBudget budget) {
    return globalSearchService.search(userId, query, budget);
  }
}
