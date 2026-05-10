package com.dto.profile;

import lombok.Data;

/**
 * 初始化用户画像请求
 */
@Data
public class ProfileInitRequest {

  /**
   * 用户ID
   */
  private Long userId;

  /**
   * 用户输入的目标岗位
   */
  private String targetRole;

  /**
   * 用户输入的简历/项目经历文本
   */
  private String resumeText;
}
