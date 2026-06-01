package com.ai.decision;

import org.springframework.stereotype.Service;

import com.common.RetrievalSlotType;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RetrievalSlottingService {

  public RetrievalSlottingDecision slot(String question, String answer) {
    String text = ((question == null ? "" : question)
        + " "
        + (answer == null ? "" : answer))
        .trim()
        .toLowerCase();
    if (text.isBlank()) {
      return noRag(
          "empty_input");
    }
    if (text.length() < 8) {
      return light(
          "short_input");
    }
    // 寒暄/控制语句
    if (isControlIntent(text)) {
      return noRag(
          "control_intent");
    }
    /*
     * 4 默认完整检索
     */
    return full(
        "technical_or_contextual");
  }

  private boolean isControlIntent(
      String text) {

    return text.contains("继续")
        || text.contains("下一题")
        || text.contains("谢谢")
        || text.contains("明白")
        || text.contains("继续吧")
        || text.contains("ok");
  }

  private RetrievalSlottingDecision full(String reason) {
    return RetrievalSlottingDecision.builder()
        .skipRetrieval(false)
        .reason(reason)
        .slotType(RetrievalSlotType.FULL_RAG)
        .build();
  }

  private RetrievalSlottingDecision light(String reason) {
    return RetrievalSlottingDecision.builder()
        .skipRetrieval(false)
        .reason(reason)
        .slotType(RetrievalSlotType.LIGHT_RAG)
        .build();
  }

  private RetrievalSlottingDecision noRag(String reason) {
    return RetrievalSlottingDecision.builder()
        .skipRetrieval(true)
        .reason(reason)
        .slotType(RetrievalSlotType.NO_RAG)
        .build();
  }

}
