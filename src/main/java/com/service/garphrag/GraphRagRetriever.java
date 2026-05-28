package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Service;

import com.common.GraphRagMode;
import com.dto.graphrag.GraphRagQueryPlan;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
// GraphRAG 的“统一检索入口”
public class GraphRagRetriever {
  private final GraphRagLocalSearchService localSearchService;
  private final GraphRagGlobalSearchService globalSearchService;
  private final GraphRagDriftSearchService driftSearchService;
  private final GraphRagQueryClassifier queryClassifier;

  public List<String> retrieve(Long userId, String query, GraphRagMode mode, boolean realTime) {
    GraphRagMode finalMode = mode;
    if (mode == GraphRagMode.AUTO) {
      GraphRagQueryPlan plan = queryClassifier.classify(query, realTime);
      finalMode = plan.getMode();
    }
    return switch (finalMode) {
      case LOCAL -> localSearchService.search(userId, query);
      case GLOBAL -> globalSearchService.search(userId, query);
      case DRIFT -> driftSearchService.search(userId, query);
      default -> throw new IllegalArgumentException("Unexpected value: " + finalMode);
    };
  }

  public List<String> retrieveForRealtime(Long userId, String query) {
    return retrieve(userId, query, GraphRagMode.AUTO, true);
  }

  public List<String> retrieveForAnalysis(Long userId, String query) {
    return retrieve(userId, query, GraphRagMode.AUTO, false);
  }
}
