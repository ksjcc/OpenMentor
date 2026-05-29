package com.ai.orchestrator.pipeline;

import java.util.List;

import com.dto.graphrag.GraphRagQueryPlan;
import com.dto.graphrag.RetrievalPlanDTO;
import com.service.garphrag.RetrievalBudget;
import com.service.garphrag.GraphRagQueryClassifier;
import com.service.garphrag.GraphRagRetriever;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphRagEnrichmentStage {
  private final GraphRagRetriever graphRagRetriever;
  private final GraphRagQueryClassifier graphRagQueryClassifier;

  public void execute(InterviewPipelineContext context) {
    RetrievalPlanDTO retrievalPlan = context.getRetrievalPlan();
    RetrievalBudget budget = context.getRetrievalBudget();
    if (retrievalPlan == null || !retrievalPlan.isNeedGraph()) {
      context.setGraphRagContext(List.of());
      return;
    }
    if (budget == null || budget.getRemainingRetrieverCalls() <= 0) {
      context.setGraphRagContext(List.of());
      context.setGraphFallbackTriggered(true);
      return;
    }

    String query = retrievalPlan.getQuery() == null || retrievalPlan.getQuery().isBlank()
        ? context.getQuestion() + "\n" + context.getAnswer()
        : retrievalPlan.getQuery();

    GraphRagQueryPlan queryPlan = graphRagQueryClassifier.classify(
        context.getUserId(),
        query,
        context.isRealTime());

    List<String> graphRagContext = graphRagRetriever.retrieve(
        context.getUserId(),
        query,
        retrievalPlan.getGraphMode() == null ? queryPlan.getMode() : retrievalPlan.getGraphMode(),
        context.isRealTime(),
        retrievalPlan.getTopK() == null ? queryPlan.getMaxResults() : retrievalPlan.getTopK(),
        budget);

    context.setGraphRagContext(graphRagContext);
    context.setUsedGraphMode(retrievalPlan.getGraphMode() == null ? queryPlan.getMode() : retrievalPlan.getGraphMode());
  }
}
