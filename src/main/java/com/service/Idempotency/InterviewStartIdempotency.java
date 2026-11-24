package com.service.Idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ai.resilience.IdempotencyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewStartIdempotency {
  private final IdempotencyGuard guard;
  @Value("${interview.idempotency.lock-ttl-seconds:60}")
  private long lockTtlSeconds;// 锁的过期时间

  @Value("${interview.idempotency.explicit-result-ttl-seconds:300}")
  private long explicitResultTtlSeconds;// 显式结果缓存时长

  @Value("${interview.idempotency.implicit-result-ttl-seconds:10}")
  private long implicitResultTtlSeconds;// 隐式结果缓存时长

  public String scope(Long userId, String idempotencykey, String interviewType, String targetRole, String topic,
      String profile) {
    if (idempotencykey != null && !idempotencykey.isBlank()) {
      return "start:%d:k:%s".formatted(userId, sanitize(idempotencykey));
    }
    String raw = "%d|%s|%s|%s|%s".formatted(
        userId,
        nullToEmpty(interviewType),
        nullToEmpty(targetRole),
        nullToEmpty(topic),
        nullToEmpty(profile));
    return "start:%d:h:%s".formatted(userId, sha256(raw));
  }

  public Duration resultTtl(String idempotencyKey) {
    long seconds = (idempotencyKey != null && !idempotencyKey.isBlank())
        ? explicitResultTtlSeconds
        : implicitResultTtlSeconds;
    return Duration.ofSeconds(Math.max(1, seconds));
  }

  public Duration lockTtl() {
    return Duration.ofSeconds(Math.max(5, lockTtlSeconds));
  }

  public String tryLock(String scope) {
    return guard.tryLock(scope, lockTtl());
  }

  public void unlock(String scope, String token) {
    guard.unlock(scope, token);
  }

  public String findExistingSession(String scope) {
    return guard.findResult(scope);
  }

  public void rememberSession(String scope, Long sessionId, String idempotencyKey) {
    guard.remember(scope, String.valueOf(sessionId), resultTtl(idempotencyKey));
  }

  private String sanitize(String key) {
    String trimmed = key.trim();
    return trimmed.length() > 64 ? sha256(trimmed) : trimmed.replaceAll("[^A-Za-z0-9_.:-]", "_");
  }

  private String sha256(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, 32);
    } catch (Exception ex) {
      log.warn("哈希失败，退回 hashCode", ex);
      return Integer.toHexString(raw.hashCode());
    }
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
