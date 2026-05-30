package com.ai.orchestrator.pipeline;

import java.util.List;
import java.util.Map;

import com.dto.graphrag.GraphRagRetrievalPlan;
import com.dto.graphrag.RetrievalPlanDTO;
import com.entity.Trace.TraceEvent;
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
    long start = System.currentTimeMillis();
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

    GraphRagRetrievalPlan queryPlan = graphRagQueryClassifier.classify(
        context.getUserId(),
        query,
        context.isRealTime());

    GraphRagRetrievalPlan effectivePlan = retrievalPlan.getGraphMode() == null
        ? queryPlan
        : GraphRagRetrievalPlan.builder()
            .mode(retrievalPlan.getGraphMode())
            .retrievers(queryPlan.getRetrievers())
            .needRerank(queryPlan.isNeedRerank())
            .budget(queryPlan.getBudget())
            .reason(queryPlan.getReason())
            .build();

    List<String> graphRagContext = graphRagRetriever.retrieve(
        context.getUserId(),
        query,
        effectivePlan,
        retrievalPlan.getTopK() == null ? 6 : retrievalPlan.getTopK(),
        budget);

    context.setGraphRagContext(graphRagContext);
    context.setUsedGraphMode(effectivePlan.getMode());
    long end = System.currentTimeMillis();
    TraceEvent event = TraceEvent.builder()
        .stage("graph_rag")
        .startTime(start)
        .endTime(end)
        .latencyMs(end - start)
        .input(query)
        .metadata(Map.of(
            "retrievedCount", graphRagContext.size(),
            "usedMode", effectivePlan.getMode().name(),
            "reason", effectivePlan.getReason(),
            "budgetState", budget.summary()))
        .build();

    context.getTraceContext()
        .addEvent(event);
  }
}
