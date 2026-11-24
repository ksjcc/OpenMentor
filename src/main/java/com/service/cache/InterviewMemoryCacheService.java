package com.service.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.entity.InterviewMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 面试短期记忆缓存。
 *
 * <p>
 * Redis List 保存一场面试最近 20 条消息，作为 Prompt 的短期上下文。
 * MySQL 仍是最终来源，Redis 只是加速层：Redis 不可用时进程内保留一份同样的
 * 最近 20 条，读写都不抛异常，面试流程不会因为缓存挂了而中断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewMemoryCacheService {
    private static final String KEY_TEMPLATE = "interview:session:%d:messages";
    private static final int MAX_MESSAGES = 20;
    /** 本地兜底最多保留多少场会话，避免内存无限增长。 */
    private static final int MAX_LOCAL_SESSIONS = 200;

    private final RedisTemplate<String, Object> redisTemplate;

    /** sessionId -> 最近 N 条消息（按写入顺序） */
    private final Map<Long, List<InterviewMessage>> localCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<InterviewMessage>> eldest) {
                    return size() > MAX_LOCAL_SESSIONS;
                }
            });

    public void cacheMessage(Long sessionId, InterviewMessage message) {
        validateSessionId(sessionId);
        Objects.requireNonNull(message, "缓存消息不能为空");

        appendLocal(sessionId, message);

        try {
            String key = KEY_TEMPLATE.formatted(sessionId);
            redisTemplate.opsForList().rightPush(key, message);
            redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
        } catch (Exception ex) {
            log.debug("Redis 短期缓存不可用，使用进程内缓存 sessionId={} reason={}", sessionId, ex.getMessage());
        }
    }

    public List<InterviewMessage> getRecentMessages(Long sessionId) {
        validateSessionId(sessionId);

        try {
            List<Object> cachedMessages = redisTemplate.opsForList()
                    .range(KEY_TEMPLATE.formatted(sessionId), 0, -1);
            if (cachedMessages != null && !cachedMessages.isEmpty()) {
                List<InterviewMessage> fromRedis = cachedMessages.stream()
                        .filter(InterviewMessage.class::isInstance)
                        .map(InterviewMessage.class::cast)
                        .toList();
                if (!fromRedis.isEmpty()) {
                    return fromRedis;
                }
            }
        } catch (Exception ex) {
            log.debug("Redis 短期缓存不可用，读取进程内缓存 sessionId={} reason={}", sessionId, ex.getMessage());
        }

        return readLocal(sessionId);
    }

    public String buildRecentContext(Long sessionId) {
        List<InterviewMessage> messages = getRecentMessages(sessionId);
        if (messages.isEmpty()) {
            return "暂无短期上下文。";
        }

        StringBuilder context = new StringBuilder();
        for (InterviewMessage message : messages) {
            context.append(message.getRole())
                    .append("（")
                    .append(message.getAgentCode())
                    .append("）：")
                    .append(message.getContent())
                    .append("\n");
        }
        return context.toString();
    }

    public String buildtContext(Long sessionId) {
        return buildRecentContext(sessionId);
    }

    // ==================== 进程内兜底 ====================

    private void appendLocal(Long sessionId, InterviewMessage message) {
        List<InterviewMessage> messages = localCache.computeIfAbsent(sessionId, key -> new ArrayList<>());
        messages.add(message);
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    private List<InterviewMessage> readLocal(Long sessionId) {
        List<InterviewMessage> messages = localCache.get(sessionId);
        return messages == null ? Collections.emptyList() : List.copyOf(messages);
    }

    private String buildKey(Long sessionId) {
        return KEY_TEMPLATE.formatted(sessionId);
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("面试ID不能为空");
        }
    }
}
