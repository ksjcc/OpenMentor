package com.dto.graph;

import lombok.Data;

/**
 * 图谱节点 DTO
 */
@Data
public class GraphNodeDTO {
  /**
   * 节点ID
   */
  private Long id;

  /**
   * 知识点名称
   */
  private String name;

  /**
   * 节点类型
   */
  private String type;

  /**
   * 用户掌握度
   */
  private Integer masteryLevel;

  /**
   * AI重要度
   */
  private Integer importanceScore;
}
