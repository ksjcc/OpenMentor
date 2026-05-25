package com.ai.decision;

import com.dto.decision.AgentDecisionDTO;
import com.dto.decision.FinalDecisionDTO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 多 Agent 决策聚合器
 *
 * 作用：
 * 将 Score / Graph / Memory / Difficulty 等多个模块的建议合并成最终动作。
 *
 * 当前使用简单规则：
 * 1. 如果高置信度 FOLLOW_UP >= 1，优先 FOLLOW_UP
 * 2. 如果 Difficulty 建议 HARDER 且没有强 FOLLOW_UP，则 HARDER
 * 3. 默认 NEXT
 */
@Component
public class DecisionAggregator {
  public FinalDecisionDTO aggregate(List<AgentDecisionDTO> decisions) {

    FinalDecisionDTO finalDecision = new FinalDecisionDTO();

    finalDecision.setAgentDecisions(decisions);

    // 1. 优先找高置信度 FOLLOW_UP
    AgentDecisionDTO followUp = decisions.stream()
        .filter(d -> d.getAction() == DecisionAction.FOLLOW_UP)
        .filter(d -> d.getConfidence() != null && d.getConfidence() >= 0.75)
        .max(Comparator.comparing(AgentDecisionDTO::getConfidence))
        .orElse(null);

    if (followUp != null) {
      finalDecision.setAction(DecisionAction.FOLLOW_UP);
      finalDecision.setTopic(followUp.getRecommendedTopic());
      finalDecision.setReason("多个决策模块认为需要继续追问：" + followUp.getReason());
      return finalDecision;
    }

    // 2. 找 HARDER
    AgentDecisionDTO harder = decisions.stream()
        .filter(d -> d.getAction() == DecisionAction.HARDER)
        .findFirst()
        .orElse(null);

    if (harder != null) {
      finalDecision.setAction(DecisionAction.HARDER);
      finalDecision.setTopic(harder.getRecommendedTopic());
      finalDecision.setReason("用户回答较好，系统决定提高难度：" + harder.getReason());
      return finalDecision;
    }

    // 3. 默认 NEXT
    finalDecision.setAction(DecisionAction.NEXT);
    finalDecision.setReason("用户回答基本通过，进入下一题。");

    return finalDecision;
  }
}
