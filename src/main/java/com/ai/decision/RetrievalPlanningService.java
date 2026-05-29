package com.ai.decision;

import com.common.GraphRagMode;
import com.dto.graphrag.RetrievalPlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RetrievalPlanningService {
  public RetrievalPlanDTO plan(String question, String answer, String weakTopic) {
    boolean needGraph = false;
    boolean needMemory = true;
    boolean needHyde = false;
    GraphRagMode mode = GraphRagMode.LOCAL;
    String query = weakTopic == null ? question + "\n" + answer
        : weakTopic;
    if (containsTechConcept(query)) {
      needGraph = true;
      needHyde = true;
    }
    if (containsCrossDomain(query)) {
      mode = GraphRagMode.GLOBAL;
    }
    RetrievalPlanDTO plan = RetrievalPlanDTO.builder()
        .needMemory(needMemory)
        .needGraph(needGraph)
        .needHyde(needHyde)
        .graphMode(mode)
        .topK(5)
        .query(query)
        .reason(buildReason(
            needMemory,
            needGraph,
            needHyde,
            mode))
        .build();

    log.info("RetrievalPlan={}", plan);

    return plan;
  }

  private boolean containsTechConcept(String text) {
    if (text == null) {
      return false;
    }

    String lower = text.toLowerCase();

    return lower.contains("redis")
        || lower.contains("mysql")
        || lower.contains("jvm")
        || lower.contains("线程")
        || lower.contains("缓存");
  }

  private boolean containsCrossDomain(String text) {
    if (text == null) {
      return false;
    }

    String lower = text.toLowerCase();

    return lower.contains("一致性")
        || lower.contains("事务")
        || lower.contains("架构");
  }

  private String buildReason(
      boolean memory,
      boolean graph,
      boolean hyde,
      GraphRagMode mode) {

    return String.format(
        "memory=%s graph=%s hyde=%s mode=%s",
        memory,
        graph,
        hyde,
        mode);
  }
}
