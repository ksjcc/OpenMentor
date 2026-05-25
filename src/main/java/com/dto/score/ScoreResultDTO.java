package com.dto.score;

import lombok.Data;

import java.util.List;

/**
 * ScoreAgent 返回结果
 */
@Data
public class ScoreResultDTO {

  /**
   * 总分 0~100
   */
  private Integer score;

  /**
   * 技术正确性
   */
  private Integer technical;

  /**
   * 完整性
   */
  private Integer completeness;

  /**
   * 表达能力
   */
  private Integer expression;

  /**
   * 是否薄弱
   */
  private Boolean isWeak;

  /**
   * 评价
   */
  private String feedback;

  /**
   * 薄弱点
   */
  private List<String> weakPoints;
}
