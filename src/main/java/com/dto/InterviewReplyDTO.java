package com.dto;

import java.util.Map;

import lombok.Data;

/**
 * 面试交互返回对象
 * 用于返回给前端：
 * 用户ID
 * 当前主题
 * AI下一句话
 * 下一步动作类型
 * 评分结果
 */
@Data
public class InterviewReplyDTO {
  private Long sessionId;
  private String content;
  private String action;
  private String scoreJson;
  private Map<String, Long> stageLatencyMs;
  private Long totalLatencyMs;

}
