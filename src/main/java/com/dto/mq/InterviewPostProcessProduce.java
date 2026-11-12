package com.dto.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.common.RabbitMQConstants;
import com.entity.InterviewSession;
import com.mapper.InterviewSessionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 面试后处理消息生产者
 * finishInterview() 中不再同步做所有后处理，
 * 而是把任务发送到 RabbitMQ。
 */
@Component
@RequiredArgsConstructor
public class InterviewPostProcessProduce {
  private final RabbitTemplate rabbitTemplate;
  private final InterviewSessionMapper interviewSessionMapper;

  private static final String STATUS_PENDING = "PENDING";

  /**
   * 发送面试后处理消息
   */
  public void send(InterviewPostProcessMessage message) {
    markPending(message);
    rabbitTemplate.convertAndSend(
        RabbitMQConstants.INTERVIEW_EXCHANGE,
        RabbitMQConstants.INTERVIEW_POST_PROCESS_ROUTING_KEY,
        message);
  }

  private void markPending(InterviewPostProcessMessage message) {
    if (message == null || message.getSessionId() == null) {
      return;
    }

    InterviewSession session = new InterviewSession();
    session.setId(message.getSessionId());
    session.setPostProcessStatus(STATUS_PENDING);
    session.setPostProcessRetryCount(0);
    session.setPostProcessError(null);
    session.setPostProcessFinishedAt(null);
    interviewSessionMapper.updateById(session);
  }
}
