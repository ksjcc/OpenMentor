package com.service.cache;

import com.entity.InterviewMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 面试短期记忆缓存。
 *
 * 目标：
 * 使用 Redis List 保存一场面试最近 N 条消息，作为 Agent Prompt 的短期上下文。
 *
 * Redis Key：
 * interview:session:{sessionId}:messages
 *
 * Redis 数据结构：
 * List
 *
 * 写入策略：
 * 1. RPUSH：新消息追加到列表尾部，保持消息从旧到新的自然顺序。
 * 2. LTRIM：每次写入后只保留最后 20 条，避免短期记忆无限增长。
 *
 * 注意：
 * MySQL 仍然是面试消息的最终持久化来源；Redis 只承担高频短期读取和 Prompt 上下文缓存。
 */
@Service
@RequiredArgsConstructor
public class InterviewMemoryCacheService {
    private static final String KEY_TEMPLATE = "interview:session:%d:messages";
    private static final int MAX_MESSAGES = 20;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存一条面试消息。
     *
     * 接入位置：
     * InterviewService.saveMessage(...) 在写入 MySQL 成功后调用本方法。
     *
     * @param sessionId 面试会话 ID
     * @param message   已保存到数据库的 InterviewMessage
     */
    public void cacheMessage(Long sessionId, InterviewMessage message) {
        validateSessionId(sessionId);
        Objects.requireNonNull(message, "缓存消息不能为空");

        String key = buildKey(sessionId);

        // 追加到 List 尾部，保证 Redis 中的消息顺序与对话发生顺序一致。
        redisTemplate.opsForList().rightPush(key, message);

        // 只保留最近 20 条消息：start = -20, end = -1 表示列表尾部最近 20 条。
        redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
    }

    /**
     * 获取最近 20 条面试消息。
     *
     * Agent 构建 Prompt 时可以优先读取 Redis：
     * 1. 命中 Redis：直接使用短期记忆，减少 MySQL interview_message 查询。
     * 2. 未命中 Redis：可以由调用方降级查询 MySQL，并在后续新消息写入时自然重新建立缓存。
     *
     * @param sessionId 面试会话 ID
     * @return 按时间从旧到新排列的最近消息列表
     */
    public List<InterviewMessage> getRecentMessages(Long sessionId) {
        validateSessionId(sessionId);

        List<Object> cachedMessages = redisTemplate.opsForList().range(buildKey(sessionId), 0, -1);
        if (cachedMessages == null || cachedMessages.isEmpty()) {
            return Collections.emptyList();
        }

        return cachedMessages.stream()
                .filter(InterviewMessage.class::isInstance)
                .map(InterviewMessage.class::cast)
                .toList();
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

    /**
     * 构建项目统一的短期记忆 Redis Key。
     */
    private String buildKey(Long sessionId) {
        return KEY_TEMPLATE.formatted(sessionId);
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("面试ID不能为空");
        }
    }
}
