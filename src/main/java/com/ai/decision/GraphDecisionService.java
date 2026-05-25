package com.ai.decision;

import com.dto.decision.AgentDecisionDTO;
import com.dto.graph.GraphReasoningResult;
import com.service.graph.GraphReasoningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 图谱决策模块
 *
 * 作用：
 * 根据 Knowledge Graph 推理结果，判断是否应该追问关联知识点。
 */
@Component
@RequiredArgsConstructor
public class GraphDecisionService {
  private final GraphReasoningService graphReasoningService;

  public AgentDecisionDTO decide(Long userId, String weakTopic) {
    AgentDecisionDTO decision = new AgentDecisionDTO();
    decision.setSource("GRAPH_REASONING");
    if (weakTopic == null || weakTopic.isBlank()) {
      decision.setAction(DecisionAction.NEXT);
      decision.setConfidence(0.3);
      decision.setReason("没有明确弱点，图谱不参与强决策。");
      return decision;
    }
    GraphReasoningResult graphResult = graphReasoningService.reasonWeakness(userId, weakTopic);
    if (graphResult.getFollowUpTopics() != null && !graphResult.getFollowUpTopics().isEmpty()) {
      String nextTopic = graphResult.getFollowUpTopics().get(0);

      decision.setAction(DecisionAction.FOLLOW_UP);
      decision.setRecommendedTopic(nextTopic);
      decision.setConfidence(0.9);
      decision.setReason(graphResult.getReasoning());

      return decision;
    }
    decision.setAction(DecisionAction.NEXT);
    decision.setRecommendedTopic(weakTopic);
    decision.setConfidence(0.5);
    decision.setReason("图谱中未找到更深层追问节点。");

    return decision;
  }

}
