package com.ai.orchestrator.pipeline;

import java.util.Map;
import static java.util.Map.entry;

import org.springframework.stereotype.Component;

import com.ai.decision.RetrievalPlanningService;
import com.ai.decision.RetrievalSlottingDecision;
import com.ai.decision.RetrievalSlottingService;
import com.ai.memory.MemoryLayerRouteDecision;
import com.ai.memory.MemoryLayerRouterService;
import com.common.RetrievalSlotType;
import com.dto.Retrieval.RetrievalPlanDTO;
import com.entity.Trace.TraceEvent;
import com.service.garphrag.RetrievalBudget;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetrievalPlanningStage {
  private final RetrievalPlanningService retrievalPlanningService;
  private final RetrievalSlottingService retrievalSlottingService;
  private final MemoryLayerRouterService memoryLayerRouterService;

  public void execute(InterviewPipelineContext context) {
    long start = System.currentTimeMillis();
    RetrievalBudget budget = context.getRetrievalBudget();
    RetrievalSlottingDecision slottingDecision = retrievalSlottingService.slot(
        context.getQuestion(),
        context.getAnswer());
    context.setRetrievalSlottingDecision(slottingDecision);

    if (slottingDecision != null && slottingDecision.isSkipRetrieval()) {
      RetrievalPlanDTO noRagPlan = RetrievalPlanDTO.builder()
          .primaryRetriever("NONE")
          .needMemory(false)
          .needVector(false)
          .needGraph(false)
          .needHyde(false)
          .enableKeywordFallback(false)
          .topK(0)
          .query(buildQuery(context))
          .reason("slotting skip retrieval: " + safeReason(slottingDecision))
          .retrievalBudget(0)
          .build();
      RetrievalPlanDTO governedPlan = applyMemoryGovernance(noRagPlan);
      context.setRetrievalPlan(governedPlan);
      recordTrace(context, start, governedPlan, slottingDecision, "skipped");
      return;
    }

    if (budget == null || budget.getRemainingRetrieverCalls() <= 0) {
      RetrievalPlanDTO exhaustedPlan = RetrievalPlanDTO.builder()
          .primaryRetriever("MEMORY_RAG")
          .needMemory(true)
          .needVector(false)
          .needGraph(false)
          .needHyde(false)
          .enableKeywordFallback(true)
          .topK(0)
          .query(buildQuery(context))
          .reason("retrieval budget exhausted, skip graph retrieval")
          .build();
      RetrievalPlanDTO governedPlan = applyMemoryGovernance(exhaustedPlan);
      context.setRetrievalPlan(governedPlan);
      recordTrace(context, start, governedPlan, slottingDecision, "budget_exhausted");
      return;
    }

    RetrievalPlanDTO plan = retrievalPlanningService.plan(context.getQuestion(),
        context.getAnswer(),
        context.getWeakTopic());
    plan = applySlotting(plan, slottingDecision);
    plan = applyMemoryGovernance(plan);
    context.setRetrievalPlan(plan);
    recordTrace(context, start, plan, slottingDecision, "planned");
  }

  private RetrievalPlanDTO applyMemoryGovernance(RetrievalPlanDTO plan) {
    if (plan == null) {
      return null;
    }

    MemoryLayerRouteDecision route = memoryLayerRouterService.route(plan.getQuery(), plan);
    if (route == null) {
      return plan;
    }

    return copyPlan(plan)
        .memoryLayer(route.getPrimaryLayer())
        .fallbackMemoryLayer(route.getFallbackLayer())
        .selectedStorage(route.getSelectedStorage())
        .fallbackStorage(route.getFallbackStorage())
        .reason(appendReason(plan.getReason(), "memoryLayer=" + route.getPrimaryLayer() + ", fallbackLayer="
            + route.getFallbackLayer() + ", routeReason=" + route.getReason()))
        .build();
  }

  private RetrievalPlanDTO applySlotting(RetrievalPlanDTO plan, RetrievalSlottingDecision slottingDecision) {
    if (plan == null || slottingDecision == null || slottingDecision.getSlotType() == null) {
      return plan;
    }

    if (slottingDecision.getSlotType() != RetrievalSlotType.LIGHT_RAG) {
      return plan;
    }

    return copyPlan(plan)
        .primaryRetriever(plan.isNeedMemory() ? "MEMORY_RAG" : plan.getPrimaryRetriever())
        .needMemory(plan.isNeedMemory())
        .needVector(plan.isNeedVector())
        .needGraph(false)
        .needHyde(false)
        .enableKeywordFallback(false)
        .graphMode(null)
        .topK(limitTopK(plan.getTopK(), 3))
        .retrievalBudget(Math.min(Math.max(plan.getRetrievalBudget(), 1), 2))
        .reason(appendReason(plan.getReason(), "slotting=LIGHT_RAG(" + safeReason(slottingDecision) + ")"))
        .build();
  }

  private RetrievalPlanDTO.RetrievalPlanDTOBuilder copyPlan(RetrievalPlanDTO plan) {
    return RetrievalPlanDTO.builder()
        .primaryRetriever(plan.getPrimaryRetriever())
        .needMemory(plan.isNeedMemory())
        .needVector(plan.isNeedVector())
        .needGraph(plan.isNeedGraph())
        .needHyde(plan.isNeedHyde())
        .enableKeywordFallback(plan.isEnableKeywordFallback())
        .graphMode(plan.getGraphMode())
        .topK(plan.getTopK())
        .query(plan.getQuery())
        .reason(plan.getReason())
        .queryType(plan.getQueryType())
        .complexity(plan.getComplexity())
        .contextDependent(plan.isContextDependent())
        .retrievalBudget(plan.getRetrievalBudget())
        .memoryLayer(plan.getMemoryLayer())
        .fallbackMemoryLayer(plan.getFallbackMemoryLayer())
        .selectedStorage(plan.getSelectedStorage())
        .fallbackStorage(plan.getFallbackStorage());
  }

  private int limitTopK(Integer topK, int maxTopK) {
    if (topK == null || topK <= 0) {
      return maxTopK;
    }
    return Math.min(topK, maxTopK);
  }

  private String buildQuery(InterviewPipelineContext context) {
    if (context.getWeakTopic() != null && !context.getWeakTopic().isBlank()) {
      return context.getWeakTopic();
    }
    String question = context.getQuestion() == null ? "" : context.getQuestion();
    String answer = context.getAnswer() == null ? "" : context.getAnswer();
    return (question + "\n" + answer).trim();
  }

  private String appendReason(String existing, String suffix) {
    if (existing == null || existing.isBlank()) {
      return suffix;
    }
    return existing + " | " + suffix;
  }

  private String safeReason(RetrievalSlottingDecision decision) {
    if (decision == null || decision.getReason() == null) {
      return "";
    }
    return decision.getReason();
  }

  private void recordTrace(
      InterviewPipelineContext context,
      long start,
      RetrievalPlanDTO plan,
      RetrievalSlottingDecision slottingDecision,
      String outcome) {
    if (context.getTraceContext() == null) {
      return;
    }

    long end = System.currentTimeMillis();
    TraceEvent event = TraceEvent.builder()
        .stage("retrieval_planning")
        .startTime(start)
        .endTime(end)
        .latencyMs(end - start)
        .input(buildQuery(context))
        .output(plan == null ? "" : plan.getReason())
        .metadata(Map.ofEntries(
            entry("outcome", outcome),
            entry("slotType", slottingDecision == null || slottingDecision.getSlotType() == null
                ? ""
                : slottingDecision.getSlotType().name()),
            entry("slotReason", safeReason(slottingDecision)),
            entry("primaryRetriever", plan == null || plan.getPrimaryRetriever() == null ? "" : plan.getPrimaryRetriever()),
            entry("memoryLayer", plan == null || plan.getMemoryLayer() == null ? "" : plan.getMemoryLayer().name()),
            entry("fallbackMemoryLayer",
                plan == null || plan.getFallbackMemoryLayer() == null ? "" : plan.getFallbackMemoryLayer().name()),
            entry("selectedStorage",
                plan == null || plan.getSelectedStorage() == null ? "" : String.join(",", plan.getSelectedStorage())),
            entry("fallbackStorage",
                plan == null || plan.getFallbackStorage() == null ? "" : String.join(",", plan.getFallbackStorage())),
            entry("needMemory", plan != null && plan.isNeedMemory()),
            entry("needVector", plan != null && plan.isNeedVector()),
            entry("needGraph", plan != null && plan.isNeedGraph()),
            entry("topK", plan == null || plan.getTopK() == null ? 0 : plan.getTopK()),
            entry("retrievalBudget", plan == null ? 0 : plan.getRetrievalBudget())))
        .build();
    context.getTraceContext().addEvent(event);
  }
}
