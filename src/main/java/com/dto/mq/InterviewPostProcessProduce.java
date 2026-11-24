package com.dto.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.common.RabbitMQConstants;
import com.entity.InterviewSession;
import com.mapper.InterviewSessionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 面试后处理消息生产者
 * finishInterview() 中不再同步做所有后处理，
 * 而是把任务发送到 RabbitMQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewPostProcessProduce {
  private final RabbitTemplate rabbitTemplate;
  private final InterviewSessionMapper interviewSessionMapper;

  private static final String STATUS_PENDING = "PENDING";

  /**
   * 发送面试后处理消息。
   *
   * <p>
   * MQ 不可用时不让"结束面试"直接报错：会话已经结束、复盘也能从库里读出来，
   * 只是闪卡/画像合并/图谱索引要等 MQ 恢复后重新投递。状态位保持 PENDING。
   */
  public void send(InterviewPostProcessMessage message) {
    markPending(message);
    try {
      rabbitTemplate.convertAndSend(
          RabbitMQConstants.INTERVIEW_EXCHANGE,
          RabbitMQConstants.INTERVIEW_POST_PROCESS_ROUTING_KEY,
          message);
    } catch (Exception ex) {
      log.warn("后处理消息投递失败，面试已结束但后处理仍为 PENDING sessionId={} reason={}",
          message == null ? null : message.getSessionId(), ex.getMessage());
    }
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
