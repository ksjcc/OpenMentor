package com.entity.graphrag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_relationship")
public class GraphRagRelationship {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /**
   * 起点实体ID
   */
  private Long sourceEntityId;

  /**
   * 终点实体ID
   */
  private Long targetEntityId;

  /**
   * 关系类型：RELATED / DEPENDS_ON / CAUSES / SOLVES / PART_OF
   */
  private String relationType;

  /**
   * 关系描述
   */
  private String description;

  /**
   * 权重 0~1
   */
  private Double weight;

  /**
   * 来源文本单元ID
   */
  private Long textUnitId;

  private LocalDateTime createdAt;
}
