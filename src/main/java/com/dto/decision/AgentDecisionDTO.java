package com.dto.decision;

import com.ai.decision.DecisionAction;
import lombok.Data;

/**
 * 单个 Agent / 模块 给出的决策建议
 *
 * 例如：
 * ScoreAgent 认为应该 FOLLOW_UP
 * GraphReasoning 认为应该追问“延迟双删”
 * DifficultyPolicy 认为应该保持当前难度
 */
@Data
public class AgentDecisionDTO {
  /**
   * 决策来源
   *
   * SCORE_AGENT
   * GRAPH_REASONING
   * MEMORY_AGENT
   * DIFFICULTY_POLICY
   * FOLLOW_UP_AGENT
   */
  private String source;

  /**
   * 建议动作
   */
  private DecisionAction action;

  /**
   * 推荐追问主题
   */
  private String recommendedTopic;

  /**
   * 置信度 0~1
   */
  private Double confidence;

  /**
   * 决策原因
   */
  private String reason;
}
