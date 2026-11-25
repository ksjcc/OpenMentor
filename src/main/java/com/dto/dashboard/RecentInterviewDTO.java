package com.dto.dashboard;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近面试 DTO
 */
@Data
public class RecentInterviewDTO {

  /**
   * 面试ID
   */
  private String sessionId;

  /**
   * 面试类型
   */
  private String interviewType;

  /**
   * 目标岗位
   */
  private String targetRole;

  /**
   * 面试状态
   */
  private String status;

  /**
   * 面试主题
   */
  private String topic;

  /**
   * 本场加权总分
   */
  private Integer score;

  /**
   * AI 提问轮次
   */
  private Integer turns;

  /**
   * 最突出的薄弱点，用于列表里给个直观提示
   */
  private String topWeakness;

  /**
   * 创建时间
   */
  private LocalDateTime createdAt;

  /**
   * 结束时间
   */
  private LocalDateTime endedAt;
}
