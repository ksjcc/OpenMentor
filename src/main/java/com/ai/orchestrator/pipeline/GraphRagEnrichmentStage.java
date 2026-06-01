package com.ai.orchestrator.pipeline;

import java.util.List;
import java.util.Map;

import com.ai.rag.HybridRetriever;
import com.dto.Retrieval.RetrievalPlanDTO;
import com.dto.graphrag.GraphRagRetrievalPlan;
import com.entity.Trace.TraceEvent;
import com.service.garphrag.RetrievalBudget;
import com.service.garphrag.GraphRagQueryClassifier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphRagEnrichmentStage {
  private final HybridRetriever hybridRetriever;
  private final GraphRagQueryClassifier graphRagQueryClassifier;

  public void execute(InterviewPipelineContext context) {
    long start = System.currentTimeMillis();
    RetrievalPlanDTO retrievalPlan = context.getRetrievalPlan();
    RetrievalBudget budget = context.getRetrievalBudget();
    if (retrievalPlan == null) {
      context.setRetrievalContext(List.of());
      context.setGraphRagContext(List.of());
      return;
    }
    if (!retrievalPlan.isNeedMemory() && !retrievalPlan.isNeedVector() && !retrievalPlan.isNeedGraph()) {
      context.setRetrievalContext(List.of());
      context.setGraphRagContext(List.of());
      context.setUsedGraphMode(null);
      context.setGraphFallbackTriggered(false);
      return;
    }
    if (budget == null || budget.getRemainingRetrieverCalls() <= 0) {
      context.setRetrievalContext(List.of());
      context.setGraphRagContext(List.of());
      context.setGraphFallbackTriggered(true);
      return;
    }

    String query = retrievalPlan.getQuery() == null || retrievalPlan.getQuery().isBlank()
        ? context.getQuestion() + "\n" + context.getAnswer()
        : retrievalPlan.getQuery();

    List<String> retrievalContext = hybridRetriever.retrieve(
        context.getUserId(),
        context.getSessionId(),
        retrievalPlan,
        budget);
    context.setRetrievalContext(retrievalContext);

    if (!retrievalPlan.isNeedGraph()) {
      context.setUsedGraphMode(null);
      context.setGraphRagContext(List.of());
      long end = System.currentTimeMillis();
      TraceEvent event = TraceEvent.builder()
          .stage("hybrid_retrieval")
          .startTime(start)
          .endTime(end)
          .latencyMs(end - start)
          .input(query)
          .metadata(Map.of(
              "retrievedCount", retrievalContext.size(),
              "memoryLayer", retrievalPlan.getMemoryLayer() == null ? "" : retrievalPlan.getMemoryLayer().name(),
              "selectedStorage", retrievalPlan.getSelectedStorage() == null ? "" : String.join(",", retrievalPlan.getSelectedStorage()),
              "fallbackStorage", retrievalPlan.getFallbackStorage() == null ? "" : String.join(",", retrievalPlan.getFallbackStorage()),
              "usedMode", retrievalPlan.getGraphMode() == null ? "" : retrievalPlan.getGraphMode().name(),
              "reason", retrievalPlan.getReason() == null ? "" : retrievalPlan.getReason(),
              "budgetState", budget.summary()))
          .build();
      if (context.getTraceContext() != null) {
        context.getTraceContext()
            .addEvent(event);
      }
      return;
    }

    GraphRagRetrievalPlan queryPlan = graphRagQueryClassifier.classify(
        context.getUserId(),
        query,
        context.isRealTime());

    GraphRagRetrievalPlan effectivePlan = queryPlan == null
        ? null
        : (retrievalPlan.getGraphMode() == null
            ? queryPlan
            : GraphRagRetrievalPlan.builder()
                .mode(retrievalPlan.getGraphMode())
                .retrievers(queryPlan.getRetrievers())
                .needRerank(queryPlan.isNeedRerank())
                .budget(queryPlan.getBudget())
                .reason(queryPlan.getReason())
                .build());

    context.setGraphRagContext(retrievalContext);
    context.setUsedGraphMode(effectivePlan == null ? retrievalPlan.getGraphMode() : effectivePlan.getMode());
    long end = System.currentTimeMillis();
    TraceEvent event = TraceEvent.builder()
        .stage("hybrid_retrieval")
        .startTime(start)
        .endTime(end)
        .latencyMs(end - start)
        .input(query)
        .metadata(Map.of(
            "retrievalContextCount", retrievalContext.size(),
            "retrievedCount", retrievalContext.size(),
            "memoryLayer", retrievalPlan.getMemoryLayer() == null ? "" : retrievalPlan.getMemoryLayer().name(),
            "selectedStorage", retrievalPlan.getSelectedStorage() == null ? "" : String.join(",", retrievalPlan.getSelectedStorage()),
            "fallbackStorage", retrievalPlan.getFallbackStorage() == null ? "" : String.join(",", retrievalPlan.getFallbackStorage()),
            "usedMode", effectivePlan == null || effectivePlan.getMode() == null ? "" : effectivePlan.getMode().name(),
            "reason", effectivePlan == null || effectivePlan.getReason() == null ? retrievalPlan.getReason() : effectivePlan.getReason(),
            "budgetState", budget.summary()))
        .build();

    if (context.getTraceContext() != null) {
      context.getTraceContext()
          .addEvent(event);
    }
  }
}
