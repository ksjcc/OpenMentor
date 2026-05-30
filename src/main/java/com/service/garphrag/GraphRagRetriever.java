package com.service.garphrag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ai.rag.RerankService;
import com.common.GraphRagMode;
import com.common.GraphRagRetrieverType;
import com.dto.graphrag.GraphRagRetrievalPlan;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraphRagRetriever {
  private final GraphRagQueryClassifier queryClassifier;
  private final GraphRagRetrievalRouter retrievalRouter;
  private final GraphRagSearchGuard searchGuard;
  private final RerankService rerankService;
  private final RetrievalBudgetService budgetService;

  public List<String> retrieve(Long userId, String query, GraphRagMode mode, boolean realTime, int topk) {
    return retrieve(userId, query, mode, realTime, topk, budgetService.createBudget());
  }

  public List<String> retrieve(
      Long userId,
      String query,
      GraphRagMode mode,
      boolean realTime,
      int topk,
      RetrievalBudget budget) {
    GraphRagRetrievalPlan plan = buildPlan(userId, query, mode, realTime);
    return retrieve(userId, query, plan, topk, budget);
  }

  public List<String> retrieve(
      Long userId,
      String query,
      GraphRagRetrievalPlan plan,
      int topk,
      RetrievalBudget budget) {
    if (budget == null || plan == null) {
      return List.of();
    }
    if (!budget.consumeRetrieverCall()) {
      return List.of();
    }

    budget.capSearchSteps(plan.getBudget());
    GraphRagSearchGuard.GraphRagSearchSession session = searchGuard.createSession(budget);
    List<List<String>> batches = runRetrievers(userId, query, plan, budget, session);
    List<String> merged = mergeResults(batches);

    if (!plan.isNeedRerank()) {
      return merged.stream().limit(topk).toList();
    }
    return rerankService.rerank(query, merged, topk, budget);
  }

  public List<String> retrieveByMode(Long userId, String query, GraphRagMode mode) {
    return retrieveByMode(userId, query, mode, budgetService.createBudget());
  }

  public List<String> retrieveByMode(Long userId, String query, GraphRagMode mode, RetrievalBudget budget) {
    return retrieve(userId, query, legacyPlan(mode), 6, budget);
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

  private GraphRagRetrievalPlan buildPlan(Long userId, String query, GraphRagMode mode, boolean realTime) {
    if (mode == null || mode == GraphRagMode.AUTO) {
      return queryClassifier.classify(userId, query, realTime);
    }
    return legacyPlan(mode);
  }

  private GraphRagRetrievalPlan legacyPlan(GraphRagMode mode) {
    GraphRagMode effectiveMode = mode == null || mode == GraphRagMode.AUTO ? GraphRagMode.LOCAL : mode;
    List<GraphRagRetrieverType> retrievers = switch (effectiveMode) {
      case GLOBAL -> List.of(GraphRagRetrieverType.GLOBAL);
      case DRIFT -> List.of(GraphRagRetrieverType.DRIFT);
      case VECTOR -> List.of(GraphRagRetrieverType.VECTOR);
      case MEMORY -> List.of(GraphRagRetrieverType.MEMORY);
      case LOCAL, AUTO -> List.of(GraphRagRetrieverType.LOCAL);
    };
    return GraphRagRetrievalPlan.builder()
        .mode(effectiveMode)
        .retrievers(retrievers)
        .needRerank(true)
        .budget(retrievers.size() + 1)
        .reason("兼容旧 GraphRagMode 入口构造的检索计划。")
        .build();
  }

  private List<List<String>> runRetrievers(
      Long userId,
      String query,
      GraphRagRetrievalPlan plan,
      RetrievalBudget budget,
      GraphRagSearchGuard.GraphRagSearchSession session) {
    List<List<String>> batches = new ArrayList<>();
    for (GraphRagRetrieverType retrieverType : plan.getRetrievers()) {
      if (!session.allow(retrieverType, query)) {
        break;
      }
      List<String> results = retrievalRouter.route(retrieverType, userId, query, budget);
      if (results != null && !results.isEmpty()) {
        batches.add(results);
      }
      if (session.isStopped()) {
        break;
      }
    }
    return batches;
  }

  private List<String> mergeResults(List<List<String>> batches) {
    Set<String> merged = new LinkedHashSet<>();
    for (List<String> batch : batches) {
      if (batch == null) {
        continue;
      }
      for (String item : batch) {
        if (item != null && !item.isBlank()) {
          merged.add(item);
        }
      }
    }
    return List.copyOf(merged);
  }
}
