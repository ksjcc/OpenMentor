package com.ai.prompt;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Prompt 上下文对象
 *
 * 作用：
 * 统一封装：
 * 1. 用户画像
 * 2. 长期记忆
 * 3. 短期记忆
 * 4. 当前问题
 * 5. 当前回答
 * 6. AI评分结果
 *
 * PromptBuilder 会根据这个对象
 * 构建最终 Prompt。
 */
@Data
@Builder(toBuilder = true)
public class PromptContext {

  /**
   * 用户ID
   */
  private Long userId;

  /**
   * 面试ID
   */
  private Long sessionId;

  /**
   * 用户画像 JSON
   */
  private String profileJson;

  /**
   * 长期 Markdown 总结
   */
  private String summaryMd;

  /**
   * RAG 召回结果
   */
  private List<String> ragMemories;

  /**
   * Redis 最近上下文
   */
  private String recentContext;

  /**
   * 当前问题
   */
  private String currentQuestion;

  /**
   * 用户当前回答
   */
  private String currentAnswer;

  /**
   * ScoreAgent JSON结果
   */
  private String scoreJson;

  /**
   * 当前主题
   */
  private String topic;

  /**
   * 目标岗位
   */
  private String targetRole;

  /**
   * 简历 / 项目经历文本
   */
  private String resumeText;

  /**
   * 本场面试完整对话
   */
  private String dialogue;

  /**
   * 当前任务
   *
   * FOLLOW_UP
   * NEW_QUESTION
   * HARDER
   * SCORE
   * DECISION
   * EXTRACTION
   * PROFILE
   */
  private String task;
  /**
   * Knowledge Graph 推理结果
   */
  private String graphReasoning;

  /**
   * GraphRAG / 其他扩展任务的原始输入
   */
  private String rawInput;

  private String projectSummary;
  private List<String> recommends;
}
