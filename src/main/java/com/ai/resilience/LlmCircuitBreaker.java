package com.ai.resilience;

import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 模型调用熔断器。
 *
 *
 * 状态机：
 *
 * CLOSED ──失败率超阈值且样本足够──▶ OPEN
 * OPEN ──等待 openDurationMs────▶ HALF_OPEN（只放 1 个探测请求）
 * HALF_OPEN ──探测成功──▶ CLOSED（清空窗口）
 * HALF_OPEN ──探测失败──▶ OPEN（重新计时）
 * 为什么手写而不是引 Resilience4j：这里只需要"最近 N 次失败率"这一个能力，
 * 为它引入一个额外依赖（还要配 AOP）不划算。60 行足够，行为可读、可测。
 *
 * <p>
 * 注意：只统计"模型/超时"类失败。参数错误、业务校验失败不应该算进熔断。
 */
@Component
public class LlmCircuitBreaker {

  public enum State {
    CLOSED, OPEN, HALF_OPEN
  }

  private final int windowSize;
  private final int minSamples;
  private final double failureRateThreshold;
  private final long openDurationMs;

  private final Deque<Boolean> window = new ArrayDeque<>();

  private State state = State.CLOSED;
  private long openedAt = 0L;
  private boolean halfOpenProbeInFlight = false;

  private long successCount = 0;
  private long failureCount = 0;
  private long rejectedCount = 0;
  private long openedCount = 0;

  public LlmCircuitBreaker(
      @Value("${interview.breaker.window-size:20}") int windowSize,
      @Value("${interview.breaker.min-samples:8}") int minSamples,
      @Value("${interview.breaker.failure-rate:0.5}") double failureRateThreshold,
      @Value("${interview.breaker.open-duration-ms:30000}") long openDurationMs) {
    this.windowSize = Math.max(4, windowSize);
    this.minSamples = Math.max(2, Math.min(minSamples, this.windowSize));
    this.failureRateThreshold = Math.min(1.0, Math.max(0.05, failureRateThreshold));
    this.openDurationMs = Math.max(1000L, openDurationMs);
  }

  /** 是否允许发起一次调用。 */
  public synchronized boolean tryAcquire() {
    switch (state) {
      case CLOSED:
        return true;
      case OPEN:
        if (System.currentTimeMillis() - openedAt >= openDurationMs) {
          state = State.HALF_OPEN;
          halfOpenProbeInFlight = true;
          return true;
        }
        rejectedCount++;
        return false;
      case HALF_OPEN:
        if (!halfOpenProbeInFlight) {
          halfOpenProbeInFlight = true;
          return true;
        }
        rejectedCount++;
        return false;
      default:
        return false;
    }
  }

  public synchronized void onSuccess() {
    successCount++;
    if (state == State.HALF_OPEN) {
      state = State.CLOSED;
      halfOpenProbeInFlight = false;
      window.clear();
      return;
    }
    if (state == State.CLOSED) {
      push(true);
    }
  }

  public synchronized void onFailure() {
    failureCount++;
    if (state == State.HALF_OPEN) {
      state = State.OPEN;
      openedAt = System.currentTimeMillis();
      halfOpenProbeInFlight = false;
      openedCount++;
      return;
    }
    if (state == State.CLOSED) {
      push(false);
      if (window.size() >= minSamples && failureRate() >= failureRateThreshold) {
        state = State.OPEN;
        openedAt = System.currentTimeMillis();
        openedCount++;
      }
    }
  }

  /** 并发闸拒绝时调用：既不算成功也不算失败，但要清掉 HALF_OPEN 的探测占位。 */
  public synchronized void onAborted() {
    if (state == State.HALF_OPEN) {
      halfOpenProbeInFlight = false;
    }
  }

  public synchronized State state() {
    return state;
  }

  public synchronized String summary() {
    return "state=%s window=%d/%d failureRate=%.2f success=%d failure=%d rejected=%d opened=%d"
        .formatted(state, window.size(), windowSize, failureRate(),
            successCount, failureCount, rejectedCount, openedCount);
  }

  private void push(boolean ok) {
    window.addLast(ok);
    while (window.size() > windowSize) {
      window.removeFirst();
    }
  }

  private double failureRate() {
    if (window.isEmpty()) {
      return 0.0;
    }
    long failures = window.stream().filter(ok -> !ok).count();
    return (double) failures / window.size();
  }
}