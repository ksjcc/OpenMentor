package com.ai.decision;

import com.dto.decision.AgentDecisionDTO;
import com.dto.score.ScoreResultDTO;
import org.springframework.stereotype.Component;

/**
 * 难度策略模块
 *
 * 它不是大模型，而是规则决策模块。
 *
 * 作用：
 * 根据分数判断下一步难度：
 * - 低分：追问
 * - 中等：下一题
 * - 高分：提高难度
 */
@Component
public class DifficultyPolicy {
  public AgentDecisionDTO decide(ScoreResultDTO scoreResult) {

    AgentDecisionDTO decision = new AgentDecisionDTO();

    decision.setSource("DIFFICULTY_POLICY");
    decision.setConfidence(0.8);

    int score = scoreResult.getScore() == null
        ? 0
        : scoreResult.getScore();

    if (score < 60) {
      decision.setAction(DecisionAction.FOLLOW_UP);
      decision.setReason("用户回答分数低于60，需要继续追问当前知识点。");
      return decision;
    }

    if (score >= 85) {
      decision.setAction(DecisionAction.HARDER);
      decision.setReason("用户回答质量较高，可以提高问题难度。");
      return decision;
    }

    decision.setAction(DecisionAction.NEXT);
    decision.setReason("用户回答基本合格，可以进入下一题。");

    return decision;
  }
}
