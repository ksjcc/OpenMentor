package com.dto.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.common.RabbitMQConstants;

import lombok.RequiredArgsConstructor;

/**
 * 面试后处理消息生产者
 *
 * 作用：
 * finishInterview() 中不再同步做所有后处理，
 * 而是把任务发送到 RabbitMQ。
 */
@Component
@RequiredArgsConstructor
public class InterviewPostProcessProduce {
  private final RabbitTemplate rabbitTemplate;

  /**
   * 发送面试后处理消息
   */
  public void send(InterviewPostProcessMessage message) {
    rabbitTemplate.convertAndSend(
        RabbitMQConstants.INTERVIEW_EXCHANGE,
        RabbitMQConstants.INTERVIEW_POST_PROCESS_ROUTING_KEY,
        message);
  }
}
