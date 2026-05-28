package com.ai.orchestrator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dto.decision.AgentDecisionDTO;

import lombok.Data;

@Data
public class OrchestratorResult {

  /**
   * Orchestrator 返回结果
   */

  /**
   * 最终动作
   */
  private String action;

  /**
   * AI 下一步输出
   */
  private String content;

  /**
   * ScoreAgent 原始评分 JSON
   */
  private String scoreJson;

  /**
   * 决策原因
   */
  private String decisionReason;

  /**
   * 各 Agent / 模块建议
   */
  private List<AgentDecisionDTO> agentDecisions;

  /**
   * 编排链路各阶段耗时（毫秒）
   */
  private Map<String, Long> stageLatencyMs = new LinkedHashMap<>();

  /**
   * 编排链路总耗时（毫秒）
   */
  private long totalLatencyMs;
}
