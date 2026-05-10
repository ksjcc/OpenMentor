package com.dto.graph;

import lombok.Data;

/**
 * 图谱边 DTO
 */
@Data
public class GraphLinkDTO {

  /**
   * 起点节点ID
   */
  private Long source;

  /**
   * 终点节点ID
   */
  private Long target;

  /**
   * 关系类型
   */
  private String relationType;

  /**
   * 关系强度
   */
  private Double weight;
}
