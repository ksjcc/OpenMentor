package com.service.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.common.BizException;
import com.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 调用限流服务。
 *
 * <p>
 * 规则：每个用户每分钟最多 10 次 AI 调用。正常走 Redis 的 INCR + EXPIRE，
 * Redis 不可用时降级为进程内计数——单个用户偶尔超限比"整个面试功能不可用"要好。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRateLimitService {
    private static final String KEY_TEMPLATE = "ai:limit:user:%d";
    private static final int MAX_CALLS_PER_MINUTE = 10;
    private static final Duration LIMIT_WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, Object> redisTemplate;

    /** 降级用的本地窗口：userId -> [窗口起始毫秒, 计数] */
    private final Map<Long, long[]> localWindows = new ConcurrentHashMap<>();

    public void checkLimit(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }

        long currentCount = increase(userId);

        if (currentCount > MAX_CALLS_PER_MINUTE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI调用过于频繁，请稍后再试");
        }
    }

    private long increase(Long userId) {
        String key = KEY_TEMPLATE.formatted(userId);
        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);
            if (currentCount == null) {
                return increaseLocally(userId);
            }
            // 第一次调用时设置过期时间，形成固定 60 秒窗口
            if (currentCount == 1L) {
                redisTemplate.expire(key, LIMIT_WINDOW);
            }
            return currentCount;
        } catch (Exception ex) {
            log.warn("限流计数降级为本地内存 userId={} reason={}", userId, ex.getMessage());
            return increaseLocally(userId);
        }
    }

    /** Redis 不可用时的进程内滑窗计数。 */
    private long increaseLocally(Long userId) {
        long now = System.currentTimeMillis();
        long windowMillis = LIMIT_WINDOW.toMillis();
        long[] window = localWindows.compute(userId, (key, existing) -> {
            if (existing == null || now - existing[0] >= windowMillis) {
                return new long[] { now, 1 };
            }
            existing[1] = existing[1] + 1;
            return existing;
        });
        return window[1];
    }
}
