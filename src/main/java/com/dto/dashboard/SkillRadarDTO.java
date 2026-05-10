package com.dto.dashboard;

import lombok.Data;

/**
 * 技能雷达图 DTO
 */
@Data
public class SkillRadarDTO {
  /**
   * 技能名称
   */
  private String skill;

  /**
   * 技能分数（0~100）
   */
  private Integer score;
}
