package com.ai.orchestrator.pipeline;

import java.util.List;

import com.service.garphrag.GraphRagRetriever;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphRagEnrichmentStage {
  private final GraphRagRetriever graphRagRetriever;

  public void execute(InterviewPipelineContext context) {
    String query = context.getQuestion() + "\n" + context.getAnswer();
    List<String> graphRagContext = context.isRealTime()
        ? graphRagRetriever.retrieveForRealtime(context.getUserId(), query)
        : graphRagRetriever.retrieveForAnalysis(context.getUserId(), query);

    context.setGraphRagContext(graphRagContext);
  }
}
