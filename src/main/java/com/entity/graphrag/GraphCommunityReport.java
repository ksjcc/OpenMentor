package com.entity.graphrag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_community_report")
public class GraphCommunityReport {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /**
   * 社区ID
   */
  private Long communityId;

  /**
   * 报告标题
   */
  private String title;

  /**
   * 社区摘要
   */
  private String summary;

  /**
   * findings JSON 字符串
   */
  private String findingsJson;

  /**
   * 社区重要度
   */
  private Double rankScore;

  /**
   * 生成报告使用的模型
   */
  private String modelName;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
