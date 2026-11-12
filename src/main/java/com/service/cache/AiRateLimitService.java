package com.service.cache;

import com.common.BizException;
import com.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AI 调用限流服务。
 *
 * 目标：
 * 限制单个用户调用 AI 的频率，避免瞬时高频请求打爆模型服务或造成费用风险。
 *
 * 限流规则：
 * 每个用户每分钟最多 10 次 AI 调用。
 *
 * Redis Key：
 * ai:limit:user:{userId}
 *
 * Redis 实现：
 * 1. INCR：每次调用 AI 前，对用户计数器加 1。
 * 2. EXPIRE：第一次计数时设置 60 秒过期时间，形成一分钟统计窗口。
 *
 * 超限行为：
 * 抛出 BizException，提示“AI调用过于频繁，请稍后再试”。
 */
@Service
@RequiredArgsConstructor
public class AiRateLimitService {
    private static final String KEY_TEMPLATE = "ai:limit:user:%d";
    private static final int MAX_CALLS_PER_MINUTE = 10;
    private static final Duration LIMIT_WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 检查并记录一次 AI 调用。
     *
     * 接入位置：
     * InterviewService.answerQuestionStream(...) 在调用 InterviewHarnessService 之前调用本方法。
     *
     * @param userId 当前用户 ID
     */
    public void checkLimit(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }

        String key = buildKey(userId);

        // INCR 是 Redis 原子操作，适合在多实例部署下统计同一个用户的调用次数。
        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI限流计数失败");
        }

        // 第一次调用时设置过期时间，形成固定 60 秒窗口。
        if (currentCount == 1L) {
            redisTemplate.expire(key, LIMIT_WINDOW);
        }

        if (currentCount > MAX_CALLS_PER_MINUTE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI调用过于频繁，请稍后再试");
        }
    }

    /**
     * 构建项目统一的 AI 限流 Redis Key。
     */
    private String buildKey(Long userId) {
        return KEY_TEMPLATE.formatted(userId);
    }
}
