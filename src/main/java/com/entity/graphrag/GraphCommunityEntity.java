package com.entity.graphrag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_community_entity")
public class GraphCommunityEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /**
   * 社区ID
   */
  private Long communityId;

  /**
   * 实体ID
   */
  private Long entityId;

  /**
   * 实体在社区中的重要度
   */
  private Double rankScore;

  private LocalDateTime createdAt;
}
