package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.common.GraphRagRetrieverType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GraphRagLocalSearchStrategy implements GraphRagSearchStrategy {
  private final GraphRagLocalSearchService localSearchService;

  @Override
  public GraphRagRetrieverType type() {
    return GraphRagRetrieverType.LOCAL;
  }

  @Override
  public List<String> search(Long userId, String query, RetrievalBudget budget) {
    return localSearchService.search(userId, query, budget);
  }
}
