package com.ai.decision;

import com.common.RetrievalSlotType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalSlottingDecision {
  /*
   * 是否执行检索
   */
  private boolean skipRetrieval;

  /*
   * 原因
   */
  private String reason;

  /*
   * slot类型
   */
  private RetrievalSlotType slotType;
}
