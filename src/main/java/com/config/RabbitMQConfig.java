package com.config;

import com.common.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

/**
 * RabbitMQ 配置类
 *
 * 这里定义：
 * 1. 交换机 Exchange
 * 2. 队列 Queue
 * 3. 绑定关系 Binding
 */
@Configuration
public class RabbitMQConfig {
  @Bean
  public DirectExchange interviewExchange() {
    return new DirectExchange(RabbitMQConstants.INTERVIEW_EXCHANGE,
        true,
        false);
  }

  /**
   * 面试后处理队列
   */
  @Bean
  public Queue interviewPostProcessQueue() {
    return QueueBuilder
        .durable(RabbitMQConstants.INTERVIEW_POST_PROCESS_QUEUE)
        .deadLetterExchange(RabbitMQConstants.INTERVIEW_EXCHANGE)
        .deadLetterRoutingKey(RabbitMQConstants.INTERVIEW_POST_PROCESS_DLQ_ROUTING_KEY)
        .build();
  }

  /**
   * 面试后处理死信队列
   */
  @Bean
  public Queue interviewPostProcessDeadLetterQueue() {
    return QueueBuilder
        .durable(RabbitMQConstants.INTERVIEW_POST_PROCESS_DLQ)
        .build();
  }

  /**
   * 绑定队列到交换机
   */
  @Bean
  public Binding interviewPostProcessBinding() {
    return BindingBuilder
        .bind(interviewPostProcessQueue())
        .to(interviewExchange())
        .with(RabbitMQConstants.INTERVIEW_POST_PROCESS_ROUTING_KEY);
  }

  /**
   * 绑定死信队列到交换机
   */
  @Bean
  public Binding interviewPostProcessDeadLetterBinding() {
    return BindingBuilder
        .bind(interviewPostProcessDeadLetterQueue())
        .to(interviewExchange())
        .with(RabbitMQConstants.INTERVIEW_POST_PROCESS_DLQ_ROUTING_KEY);
  }

  /**
   * 使用 JSON 作为 RabbitMQ 消息格式
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /**
   * 配置 RabbitTemplate 使用 JSON 转换器
   */
  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory,
      MessageConverter jsonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter);
    return template;
  }
}
