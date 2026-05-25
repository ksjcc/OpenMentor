package com.ai.decision;

import com.ai.rag.HybridRetriever;
import com.dto.decision.AgentDecisionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 长期记忆决策模块
 *
 * 作用：
 * 通过 RAG / Memory 判断用户历史上是否在某个知识点薄弱。
 */
@Component
@RequiredArgsConstructor
public class MemoryDecisionService {

  private final HybridRetriever hybridRetriever;

  public AgentDecisionDTO decide(Long userId, String topic) {

    AgentDecisionDTO decision = new AgentDecisionDTO();

    decision.setSource("MEMORY_AGENT");
    decision.setRecommendedTopic(topic);

    if (topic == null || topic.isBlank()) {
      decision.setAction(DecisionAction.NEXT);
      decision.setConfidence(0.3);
      decision.setReason("没有明确薄弱知识点，记忆模块不参与强决策。");
      return decision;
    }

    List<String> memories = hybridRetriever.retrieve(userId, topic, 5);

    boolean hitWeakMemory = memories != null
        && memories.stream()
            .anyMatch(m -> m.contains("薄弱")
                || m.contains("不会")
                || m.contains("不清楚")
                || m.contains("回答不完整"));

    if (hitWeakMemory) {
      decision.setAction(DecisionAction.FOLLOW_UP);
      decision.setConfidence(0.85);
      decision.setReason("长期记忆显示用户历史上在该知识点存在薄弱表现。");
    } else {
      decision.setAction(DecisionAction.NEXT);
      decision.setConfidence(0.5);
      decision.setReason("长期记忆中没有明显薄弱证据。");
    }

    return decision;
  }
}
