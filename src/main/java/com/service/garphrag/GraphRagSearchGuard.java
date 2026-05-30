package com.service.garphrag;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.common.GraphRagRetrieverType;

@Component
public class GraphRagSearchGuard {

  public GraphRagSearchSession createSession(RetrievalBudget budget) {
    return new GraphRagSearchSession(budget);
  }

  public static class GraphRagSearchSession {
    private final RetrievalBudget budget;
    private final Set<String> visitedQueries = new HashSet<>();

    GraphRagSearchSession(RetrievalBudget budget) {
      this.budget = budget;
      if (this.budget != null) {
        this.budget.start();
      }
    }

    public boolean allow(GraphRagRetrieverType retrieverType, String query) {
      if (budget == null) {
        return true;
      }
      if (budget.isTimedOut()) {
        return false;
      }
      if (!budget.consumeSearchStep()) {
        return false;
      }
      String key = retrieverType.name() + "::" + normalize(query);
      return visitedQueries.add(key);
    }

    public boolean isStopped() {
      return budget != null && budget.isTimedOut();
    }

    private String normalize(String query) {
      if (query == null) {
        return "";
      }
      return query.trim().replaceAll("\\s+", " ").toLowerCase();
    }
  }
}
