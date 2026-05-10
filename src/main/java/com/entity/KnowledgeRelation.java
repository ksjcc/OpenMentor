package com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_relation")
public class KnowledgeRelation {

  /**
   * 主键ID
   */
  @TableId(type = IdType.AUTO)
  private Long id;

  /**
   * 用户ID
   */
  private Long userId;

  /**
   * 起始节点ID
   *
   * source -> target
   */
  private Long sourceNodeId;

  /**
   * 目标节点ID
   */
  private Long targetNodeId;

  /**
   * 关系类型
   *
   * 可选：
   * RELATED：相关
   * DEPENDS：依赖
   * WEAKNESS：薄弱关联
   * STRONG：强项关联
   * PREREQUISITE：前置知识
   */
  private String relationType;

  /**
   * AI生成的关系说明
   *
   * 示例：
   * “缓存一致性依赖于延迟双删”
   */
  private String description;

  /**
   * 关系强度（0~1）
   *
   * 用于图谱边权重
   */
  private Double weight;

  /**
   * 来源类型
   *
   * INTERVIEW
   * FLASHCARD
   * PROJECT
   */
  private String sourceType;

  /**
   * 来源ID
   *
   * 例如：
   * sessionId
   */
  private Long sourceId;

  /**
   * 创建时间
   */
  private LocalDateTime createdAt;
}
