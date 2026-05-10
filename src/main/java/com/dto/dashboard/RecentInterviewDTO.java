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
  private Long sessionId;

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
   * 创建时间
   */
  private LocalDateTime createdAt;
}
