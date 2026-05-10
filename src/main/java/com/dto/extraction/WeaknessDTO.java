package com.dto.extraction;

import lombok.Data;

@Data
public class WeaknessDTO {
  /**
   * 薄弱点名称
   */
  private String name;

  /**
   * 严重程度（1~5）
   *
   * 1 = 轻微
   * 5 = 非常严重
   */
  private Integer level;

  /**
   * AI判断依据
   */
  private String evidence;
}
