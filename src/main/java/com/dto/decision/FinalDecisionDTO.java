package com.dto.decision;

import com.ai.decision.DecisionAction;
import lombok.Data;

import java.util.List;

/**
 * 多 Agent 协作后的最终决策结果
 */
@Data
public class FinalDecisionDTO {
  private DecisionAction action;
  private String topic;
  private String reason;
  private List<AgentDecisionDTO> agentDecisions;
}
