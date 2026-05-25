package com.dto.graph;

import lombok.Data;

import java.util.List;

/**
 * 图谱推理结果
 *
 * 用于：
 * 1. 推荐追问知识点
 * 2. 推荐学习路径
 * 3. 推荐下一阶段能力提升
 */
@Data
public class GraphReasoningResult {
  /**
   * 当前薄弱知识点
   */
  private String weakTopic;

  /**
   * 推荐继续追问的知识点
   */
  private List<String> followUpTopics;

  /**
   * 推荐学习路径
   */
  private List<String> learningPath;

  /**
   * AI推理说明
   */
  private String reasoning;
}
