package com.dto.dashboard;

import lombok.Data;

/**
 * 薄弱点排行 DTO
 */
@Data
public class WeaknessRankDTO {
  /**
   * 薄弱点名称
   */
  private String name;

  /**
   * 出现次数
   */
  private Integer count;
}
