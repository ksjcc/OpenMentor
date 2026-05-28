package com.ai.rag;

import org.springframework.stereotype.Component;

import com.entity.MemoryChunk;
import com.service.MemoryService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * 混合检索器
 *
 * 作用：
 * 1. 关键词检索：查 MySQL memory_chunk
 * 2. 向量检索：查 PostgreSQL + pgvector
 * 3. 合并去重
 */
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorStoreService vectorStoreService;
    private final MemoryService memoryService;
    private final HydeQueryExpansionService hydeQueryExpansionService;

    /**
     * 检索用户相关记忆
     *
     * @param userId 用户ID
     * @param query  当前主题 / 用户问题
     * @param limit  最大返回数量
     * @return 记忆文本列表
     */
    public List<String> retrieve(Long userId, String query, int limit) {
        List<String> result = new ArrayList<>();

        String hydeQuery = hydeQueryExpansionService
                .expand(query);

        List<String> vectorResults = vectorStoreService.search(userId, hydeQuery, limit);
        result.addAll(vectorResults);
        // 合并去重
        return new LinkedHashSet<>(result)
                .stream()
                .limit(limit)
                .toList();

    }
}
