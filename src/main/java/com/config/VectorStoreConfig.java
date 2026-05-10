package com.config;

import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置入口。
 *
 * 当前架构：
 * - MySQL：业务数据、记忆文本、面试记录
 * - PostgreSQL + pgvector：向量索引与语义检索
 */
@Configuration
public class VectorStoreConfig {
}
