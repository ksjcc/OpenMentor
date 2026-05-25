package com.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 全局配置。
 *
 * 当前项目只使用 Redis 做两件事：
 * 1. 面试短期记忆缓存：Redis List 保存最近 20 条 interview_message。
 * 2. AI 调用限流：Redis String 计数器配合 INCR + EXPIRE。
 *
 * 这里统一配置 RedisTemplate 的序列化方式，避免 Java 默认 JDK 序列化产生不可读的二进制内容。
 * key 使用字符串序列化，便于排查和运维；value 使用 JSON 序列化，便于保存 InterviewMessage 等对象。
 *
 * GenericJackson2JsonRedisSerializer 会在 JSON 中保存类型信息，
 * 因此 RedisTemplate<String, Object> 读取时可以还原成原始 Java 对象。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // Redis key 必须保持可读、稳定，方便线上通过 redis-cli 精确定位问题。
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Redis value 使用 JSON 序列化，满足本项目缓存对象的可读性和跨版本维护要求。
        // 复用 Spring Boot 的 ObjectMapper 配置，确保 LocalDateTime 等 Java 时间类型也能正确处理。
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy())
                .defaultTyping(true)
                .build();

        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(jsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);
        redisTemplate.setDefaultSerializer(jsonRedisSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
