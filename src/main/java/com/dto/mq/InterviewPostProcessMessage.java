package com.dto.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 面试结束后的异步处理消息
 *
 * 只传必要字段：
 * userId：知道处理哪个用户
 * sessionId：知道处理哪场面试
 * extractionJson：AI提炼结果
 */
@Data
public class InterviewPostProcessMessage implements Serializable {
  /**
   * 用户ID
   */
  private Long userId;

  /**
   * 面试ID
   */
  private Long sessionId;

  /**
   * KnowledgeExtractorAgent 生成的 JSON 字符串
   */
  private String extractionJson;
}
