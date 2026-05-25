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

  public List<String> retrieve(Long userId, String query, GraphRagMode mode) {
    if (mode == GraphRagMode.AUTO) {
      GraphRagQueryPlan plan = queryClassifier.classify(query, true);
      mode = plan.getMode();
    }
    return switch (mode) {
      case LOCAL -> localSearchService.search(userId, query);
      case GLOBAL -> globalSearchService.search(userId, query);
      case DRIFT -> driftSearchService.search(userId, query);
      default -> throw new IllegalArgumentException("Unexpected value: " + mode);
    };
  }

  public List<String> retrieve(Long userId, String query) {
    GraphRagQueryPlan plan = queryClassifier.classify(query, true);
    return retrieve(userId, query, plan.getMode());
  }

  public List<String> autoSearch(Long userId, String query) {
    if (isGlobalQuestion(query)) {
      return globalSearchService.search(userId, query);
    }

    if (query.contains("整体") || query.contains("总结") || query.contains("最近")) {
      return driftSearchService.search(userId, query);
    }

    return localSearchService.search(userId, query);
  }

  private boolean isGlobalQuestion(String query) {
    return query.contains("总体")
        || query.contains("整体")
        || query.contains("总结")
        || query.contains("主要问题")
        || query.contains("能力画像");
  }
}
