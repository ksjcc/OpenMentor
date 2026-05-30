package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.common.GraphRagRetrieverType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GraphRagDriftSearchStrategy implements GraphRagSearchStrategy {
  private final GraphRagDriftSearchService driftSearchService;

  @Override
  public GraphRagRetrieverType type() {
    return GraphRagRetrieverType.DRIFT;
  }

  @Override
  public List<String> search(Long userId, String query, RetrievalBudget budget) {
    return driftSearchService.search(userId, query, budget);
  }
}
