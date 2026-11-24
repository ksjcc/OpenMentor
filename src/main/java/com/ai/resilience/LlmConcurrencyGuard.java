package com.ai.resilience;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对平台整体的高并发多用户调用harenss进行控制
 * 模型侧配额和Tomcat 线程池都是全局资源，必须有实例级的总闸。
 * LlmConcurrencyGuard
 */
@Component
public class LlmConcurrencyGuard {
  private final Semaphore permits;// 允许调用证
  private final long waitMillis;// 最大等待时间

  private final AtomicInteger active = new AtomicInteger();// 活跃数
  private final AtomicInteger peak = new AtomicInteger();// 活跃数峰值
  private final AtomicLong rejected = new AtomicLong();// 被拒绝数

  public LlmConcurrencyGuard(
      @Value("${interview.guard.max-concurrent:16}") int maxConcurrent,
      @Value("${interview.guard.concurrency-wait-ms:200}") long waitMillis) {
    this.permits = new Semaphore(Math.max(1, maxConcurrent), true);// 等待的线程按 FIFO 顺序拿许可
    this.waitMillis = Math.max(0, waitMillis);
  }

  public boolean tryAcquire() {
    boolean acquired;
    try {
      acquired = permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      acquired = false;
    }
    if (!acquired) {
      rejected.incrementAndGet();
      return false;
    }
    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
    return true;
  }

  public void release() {
    active.decrementAndGet();
    permits.release();
  }

  public int active() {
    return active.get();
  }

  public int peak() {
    return peak.get();
  }

  public long rejected() {
    return rejected.get();
  }
}
