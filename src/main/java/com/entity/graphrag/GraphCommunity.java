package com.entity.graphrag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_community")
public class GraphCommunity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /**
   * 社区名称，例如 Redis 缓存一致性社区
   */
  private String name;

  private String description;

  /**
   * 社区层级，GraphRAG 中可能有多层 community
   */
  private Integer level;

  /**
   * 父社区ID，可为空
   */
  private Long parentCommunityId;

  /**
   * 社区内实体数量
   */
  private Integer entityCount;

  /**
   * 社区权重 / 重要度
   */
  private Double rankScore;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
