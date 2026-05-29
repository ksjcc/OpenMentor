package com.ai.orchestrator.pipeline;

import org.springframework.stereotype.Component;

import com.ai.decision.RetrievalPlanningService;
import com.dto.graphrag.RetrievalPlanDTO;
import com.service.garphrag.RetrievalBudget;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetrievalPlanningStage {
  private final RetrievalPlanningService retrievalPlanningService;

  public void execute(InterviewPipelineContext context) {
    RetrievalBudget budget = context.getRetrievalBudget();
    if (budget == null || budget.getRemainingRetrieverCalls() <= 0) {
      context.setRetrievalPlan(RetrievalPlanDTO.builder()
          .needMemory(true)
          .needGraph(false)
          .needHyde(false)
          .topK(0)
          .query(context.getWeakTopic() == null
              ? context.getQuestion() + "\n" + context.getAnswer()
              : context.getWeakTopic())
          .reason("retrieval budget exhausted, skip graph retrieval")
          .build());
      return;
    }

    RetrievalPlanDTO plan = retrievalPlanningService.plan(context.getQuestion(),
        context.getAnswer(),
        context.getWeakTopic());
    context.setRetrievalPlan(plan);
  }
}
