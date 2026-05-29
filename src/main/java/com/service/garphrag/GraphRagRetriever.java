package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.rag.RerankService;
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
  private final RerankService rerankService;
  private final RetrievalBudgetService budgetService;

  public List<String> retrieve(Long userId, String query, GraphRagMode mode, boolean realTime, int topk) {
    return retrieve(userId, query, mode, realTime, topk, budgetService.createBudget());
  }

  public List<String> retrieve(Long userId,
      String query,
      GraphRagMode mode,
      boolean realTime,
      int topk,
      RetrievalBudget budget) {
    if (budget == null) {
      return List.of();
    }
    if (!budget.consumeRetrieverCall()) {
      return List.of();
    }
    GraphRagMode finalMode = mode;
    if (mode == GraphRagMode.AUTO) {
      GraphRagQueryPlan plan = queryClassifier.classify(userId, query, realTime);
      finalMode = plan.getMode();
    }
    List<String> retrieved = retrieveByMode(userId, query, finalMode, budget);
    return rerankService.rerank(query, retrieved, topk, budget);
  }

  public List<String> retrieveByMode(Long userId, String query, GraphRagMode mode) {
    return retrieveByMode(userId, query, mode, budgetService.createBudget());
  }

  public List<String> retrieveByMode(Long userId, String query, GraphRagMode mode, RetrievalBudget budget) {
    return switch (mode) {
      case LOCAL -> localSearchService.search(userId, query, budget);
      case GLOBAL -> globalSearchService.search(userId, query, budget);
      case DRIFT -> driftSearchService.search(userId, query, budget);
      default -> throw new IllegalArgumentException("Unexpected value: " + mode);
    };
  }

  public List<String> retrieveForRealtime(Long userId, String query) {
    return retrieve(userId, query, GraphRagMode.AUTO, true, 6);
  }

  public List<String> retrieveForRealtime(Long userId, String query, RetrievalBudget budget) {
    return retrieve(userId, query, GraphRagMode.AUTO, true, 6, budget);
  }

  public List<String> retrieveForAnalysis(Long userId, String query) {
    return retrieve(userId, query, GraphRagMode.AUTO, false, 6);
  }

  public List<String> retrieveForAnalysis(Long userId, String query, RetrievalBudget budget) {
    return retrieve(userId, query, GraphRagMode.AUTO, false, 6, budget);
  }
}
