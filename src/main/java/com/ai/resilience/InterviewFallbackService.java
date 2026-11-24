package com.ai.resilience;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 确定性降级：模型不可用时，用"已经拿到的证据"生成一句能继续聊下去的话。
 */
@Slf4j
@Component
public class InterviewFallbackService {

  public enum Reason {
    /** 实例并发打满 */
    BUSY,
    /** 熔断打开 */
    BREAKER_OPEN,
    /** 模型调用超时 */
    TIMEOUT,
    /** 模型返回错误 */
    MODEL_ERROR,
    /** 其它未分类异常 */
    UNKNOWN
  }

  public record FallbackReply(String action, String content) {
  }

  public FallbackReply build(Reason reason, String lastWeakTopic, int turnIndex) {
    log.warn("面试进入降级 reason={} turn={} weakTopic={}", reason, turnIndex, lastWeakTopic);
    if (lastWeakTopic != null && !lastWeakTopic.isBlank()) {
      return new FallbackReply("FOLLOW_UP",
          "这题先过。我们换个方向：" + lastWeakTopic + " 这块你在实际项目里是怎么处理的？");
    }
    String hint = switch (reason) {
      case BUSY -> "现在同时面试的人有点多";
      case BREAKER_OPEN, MODEL_ERROR -> "我这边调用出了点问题";
      case TIMEOUT -> "我这边响应超时了";
      case UNKNOWN -> "刚才那条回答我没处理完";
    };
    return new FallbackReply("CLARIFY",
        hint + "，能再用一两句话讲讲你的思路吗？这一次我只围绕你说的点继续问。");
  }
}
