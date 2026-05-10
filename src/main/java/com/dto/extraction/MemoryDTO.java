package com.dto.extraction;

import lombok.Data;

import java.util.List;

@Data
public class MemoryDTO {
  /**
   * JSON长期画像更新内容
   *
   * 示例：
   * {
   * "weaknesses": [...]
   * }
   */
  private String profileUpdate;

  /**
   * Markdown长期总结
   */
  private String markdownSummary;

  /**
   * RAG 记忆片段
   *
   * 后续会：
   * 1. embedding
   * 2. 写入向量库
   */
  private List<String> chunks;
}
