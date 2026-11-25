package com.ai.resilience;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 面试创建幂等的底层存储：优先 Redis，Redis 不可用时降级为进程内本地锁/缓存。
 *
 * <p>
 * 降级不是"可用性装饰"：Redis 挂掉时如果直接抛错，用户连"开始面试"都点不了，
 * 这里用本地 Map 兜住，代价只是多实例之间不再共享幂等状态。
 */
@Slf4j
@Component
public class IdempotencyGuard {

  private static final String LOCK_TEMPLATE = "idem:lock:%s";
  private static final String RESULT_TEMPLATE = "idem:result:%s";

  private final RedisTemplate<String, Object> redisTemplate;
  private final Map<String, Long> localLocks = new ConcurrentHashMap<>();
  private final Map<String, LocalResult> localResult = new ConcurrentHashMap<>();

  public IdempotencyGuard(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  private record LocalResult(String value, long expireAt) {
  }

  public String tryLock(String scope, Duration ttl) {
    if (scope == null || scope.isBlank()) {
      return null;
    }
    String token = UUID.randomUUID().toString();
    try {
      Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey(scope), token, ttl);
      return Boolean.TRUE.equals(ok) ? token : null;
    } catch (Exception ex) {
      log.warn("幂等锁降级为本地锁 scope={} reason={}", scope, ex.getMessage());
      long now = System.currentTimeMillis();
      Long previous = localLocks.putIfAbsent(scope, now + ttl.toMillis());
      if (previous == null) {
        return "local-" + token;
      }
      if (now > previous) {
        localLocks.put(scope, now + ttl.toMillis());
        return "local-" + token;
      }
      return null;
    }
  }

  public void unlock(String scope, String token) {
    if (scope == null || token == null) {
      return;
    }
    if (token.startsWith("local-")) {
      localLocks.remove(scope);
      return;
    }
    try {
      Object current = redisTemplate.opsForValue().get(lockKey(scope));
      if (token.equals(current)) {
        redisTemplate.delete(lockKey(scope));
      }
    } catch (Exception ex) {
      log.warn("释放幂等锁失败 scope={}", scope, ex);
    }
  }

  /** 读已完成的结果；没有则返回 null。 */
  public String findResult(String scope) {
    if (scope == null || scope.isBlank()) {
      return null;
    }
    try {
      Object value = redisTemplate.opsForValue().get(resultKey(scope));
      return value == null ? null : String.valueOf(value);
    } catch (Exception ex) {
      LocalResult local = localResult.get(scope);
      if (local == null) {
        return null;
      }
      if (System.currentTimeMillis() > local.expireAt()) {
        localResult.remove(scope);
        return null;
      }
      return local.value();
    }
  }

  public void remember(String scope, String result, Duration ttl) {
    if (scope == null || result == null) {
      return;
    }
    try {
      redisTemplate.opsForValue().set(resultKey(scope), result, ttl);
    } catch (Exception ex) {
      // 只打一行原因：Redis 没起时每次点"开始面试"都会走到这里，打全栈会淹掉日志
      log.warn("幂等结果写入失败，降级为本地缓存 scope={} reason={}", scope, ex.getMessage());
      localResult.put(scope, new LocalResult(result, System.currentTimeMillis() + ttl.toMillis()));
    }
  }

  public void forget(String scope) {
    if (scope == null) {
      return;
    }
    try {
      redisTemplate.delete(resultKey(scope));
    } catch (Exception ignore) {
      // 缓存删除失败不影响主流程
    }
    localResult.remove(scope);
  }

  private String lockKey(String scope) {
    return LOCK_TEMPLATE.formatted(scope);
  }

  private String resultKey(String scope) {
    return RESULT_TEMPLATE.formatted(scope);
  }
}
