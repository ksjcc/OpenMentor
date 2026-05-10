package com.dto.graph;

import lombok.Data;

import java.util.List;

/**
 * 知识图谱 DTO
 */
@Data
public class GraphDTO {
  /**
   * 图谱节点
   */
  private List<GraphNodeDTO> nodes;

  /**
   * 图谱关系
   */
  private List<GraphLinkDTO> links;
}
