package com.common;

/**
 * RabbitMQ 常量
 *
 * 统一管理交换机、队列和路由键名称。
 */
public class RabbitMQConstants {
  /**
   * 面试后处理交换机
   */
  public static final String INTERVIEW_EXCHANGE = "careermind.interview.exchange";

  /**
   * 面试后处理队列
   */
  public static final String INTERVIEW_POST_PROCESS_QUEUE = "careermind.interview.postprocess.queue";

  /**
   * 面试后处理路由键
   */
  public static final String INTERVIEW_POST_PROCESS_ROUTING_KEY = "interview.postprocess";
}
