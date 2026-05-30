package com.service.garphrag;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.common.GraphRagRetrieverType;

@Component
public class GraphRagRetrievalRouter {
  private final Map<GraphRagRetrieverType, GraphRagSearchStrategy> strategies;

  public GraphRagRetrievalRouter(List<GraphRagSearchStrategy> strategyList) {
    this.strategies = new EnumMap<>(GraphRagRetrieverType.class);
    for (GraphRagSearchStrategy strategy : strategyList) {
      strategies.put(strategy.type(), strategy);
    }
  }

  public List<String> route(GraphRagRetrieverType retrieverType, Long userId, String query, RetrievalBudget budget) {
    GraphRagSearchStrategy strategy = strategies.get(retrieverType);
    if (strategy == null) {
      return List.of();
    }
    return strategy.search(userId, query, budget);
  }
}
