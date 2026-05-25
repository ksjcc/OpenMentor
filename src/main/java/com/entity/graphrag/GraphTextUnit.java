package com.entity.graphrag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_text_unit")
public class GraphTextUnit {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /**
   * 来源类型：INTERVIEW / RESUME / PROJECT / SUMMARY
   */
  private String sourceType;

  /**
   * 来源业务ID，例如 sessionId
   */
  private Long sourceId;

  /**
   * 文本块内容
   */
  private String content;

  /**
   * 文本块序号
   */
  private Integer chunkIndex;

  /**
   * token 数，可选
   */
  private Integer tokenCount;

  private LocalDateTime createdAt;
}