package com.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ai.rag.VectorStoreService;
import com.dto.extraction.KnowledgeExtractionResult;

import org.springframework.stereotype.Service;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.MemorySummaryMdMapper;
import com.mapper.MemoryChunkMapper;
import com.entity.MemoryProfileJson;
import com.entity.MemorySummaryMd;
import com.entity.MemoryChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {
    private final MemoryProfileJsonMapper memoryProfileJsonMapper;
    private final MemorySummaryMdMapper memorySummaryMdMapper;
    private final MemoryChunkMapper memoryChunkMapper;
    private final VectorStoreService vectorStoreService;
    private final ProfileMergeService profileMergeService;

    public void saveProfileJson(Long userId, String profileJson) {
        LambdaQueryWrapper<MemoryProfileJson> wrapper = new LambdaQueryWrapper<>();
        /**
         * 先查询当前用户的最新版本
         */
        wrapper.eq(MemoryProfileJson::getUserId, userId)
                .orderByDesc(MemoryProfileJson::getVersion)
                .last("limit 1");
        MemoryProfileJson old = memoryProfileJsonMapper.selectOne(wrapper);
        MemoryProfileJson memory = new MemoryProfileJson();
        memory.setUserId(userId);
        memory.setProfileData(profileJson);
        if (old == null) {
            memory.setVersion(1);
        } else {
            memory.setVersion((old.getVersion() == null ? 0 : old.getVersion()) + 1);
        }
        memoryProfileJsonMapper.insert(memory);
    }

    /**
     * 获取用户最新版本的profileJson
     */
    public String getLastProfileJson(Long userId) {
        LambdaQueryWrapper<MemoryProfileJson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoryProfileJson::getUserId, userId)
                .orderByDesc(MemoryProfileJson::getVersion)
                .last("limit 1");
        MemoryProfileJson memory = memoryProfileJsonMapper.selectOne(wrapper);
        if (memory == null) {
            return null;
        }
        return memory.getProfileData();
    }

    public String getLatestProfileJson(Long userId) {
        return getLastProfileJson(userId);
    }

    /**
     * 保存用户的summaryMd
     */
    public void saveSummaryMd(Long userId, String summaryType, String content) {
        MemorySummaryMd memory = new MemorySummaryMd();
        memory.setUserId(userId);
        memory.setSummaryType(summaryType);
        memory.setContent(content);
        memorySummaryMdMapper.insert(memory);
    }

    /**
     * 获取用户最新版本的summaryMd
     */
    public String getLastSummaryMd(Long userId, String summaryType) {
        LambdaQueryWrapper<MemorySummaryMd> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemorySummaryMd::getUserId, userId)
                .eq(MemorySummaryMd::getSummaryType, summaryType)
                .orderByDesc(MemorySummaryMd::getId)

                .last("limit 1");
        MemorySummaryMd memory = memorySummaryMdMapper.selectOne(wrapper);
        if (memory == null) {
            return null;
        }
        return memory.getContent();
    }

    public String getLatestSummaryMd(Long userId, String summaryType) {
        return getLastSummaryMd(userId, summaryType);
    }

    /**
     * 保存语义记忆chunk
     * 1. 保存到 MySQL 的 memory_chunk 表，方便追溯来源
     * 2. 写入向量库，方便后续 RAG 语义召回
     */
    public void saveChunk(Long userId, String sourceType, Long sourceId, String content) {
        MemoryChunk chunk = new MemoryChunk();
        chunk.setUserId(userId);
        chunk.setSourceType(sourceType);
        chunk.setSourceId(sourceId);
        chunk.setContent(content);
        chunk.setEmbedding(null); // 第一版先不做 embedding
        memoryChunkMapper.insert(chunk);
        addMemoryTextSafely(userId, chunk.getId(), content);
    }

    /**
     * 覆盖式保存：同一个来源（如某份简历）只保留最新一条片段。
     *
     * <p>
     * 简历每解析一次就会重写一次片段，不清理会不断堆积重复内容，
     * 「最近沉淀」和 RAG 召回都会被稀释。
     */
    public void replaceChunk(Long userId, String sourceType, Long sourceId, String content) {
        removeChunks(userId, sourceType, sourceId);
        saveChunk(userId, sourceType, sourceId, content);
    }

    /** 删掉某个来源已写入的片段（向量库里的旧条目不做删除，向量检索已按 id 去重）。 */
    public void removeChunks(Long userId, String sourceType, Long sourceId) {
        if (userId == null || sourceType == null || sourceId == null) {
            return;
        }
        memoryChunkMapper.delete(new LambdaQueryWrapper<MemoryChunk>()
                .eq(MemoryChunk::getUserId, userId)
                .eq(MemoryChunk::getSourceType, sourceType)
                .eq(MemoryChunk::getSourceId, sourceId));
    }

    /**
     * 关键词检索相关记忆
     */
    public List<MemoryChunk> searchRelevantChunks(Long userId, String keyword, Integer limit) {
        LambdaQueryWrapper<MemoryChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoryChunk::getUserId, userId)
                .like(MemoryChunk::getContent, keyword)
                .orderByDesc(MemoryChunk::getId)
                .last("LIMIT " + limit);

        return memoryChunkMapper.selectList(wrapper);
    }

    public List<String> retrieveRelevantMemories(Long userId, String keyword) {
        return searchRelevantChunks(userId, keyword, 5)
                .stream()
                .map(MemoryChunk::getContent)
                .toList();
    }

    /**
     * 最近写入的记忆片段（「记忆与画像」页的"最近沉淀"）。
     */
    public List<MemoryChunk> recentChunks(Long userId, Integer limit) {
        int size = limit == null || limit <= 0 ? 5 : Math.min(limit, 50);
        return memoryChunkMapper.selectList(new LambdaQueryWrapper<MemoryChunk>()
                .eq(MemoryChunk::getUserId, userId)
                .orderByDesc(MemoryChunk::getId)
                .last("LIMIT " + size));
    }

    public void saveInterviewExtraction(Long userId, Long sessionId, String extractionJson) {
        // 1. 保存为 Markdown 总结
        String md = """
                # 面试知识提炼结果

                来源面试ID：%s

                ```json
                %s
                ```
                """.formatted(sessionId, extractionJson);
        saveSummaryMd(userId, "interview-extraction", md);
        // 2. 保存为 RAG Chunk
        saveChunk(
                userId,
                "interview_extraction",
                sessionId,
                extractionJson);

        // 3. 第一版也把它写入 JSON 记忆版本
        saveProfileJson(userId, extractionJson);
    }

    /**
     * 根据 AI 提炼结果更新长期记忆
     *
     * @param userId     用户ID
     * @param sessionId  面试ID
     * @param extraction AI提炼结果
     */
    public void updateMemoryFromExtraction(Long userId, Long sessionId, KnowledgeExtractionResult extraction) {
        if (extraction == null) {
            return;
        }

        profileMergeService.mergeAndSave(userId, extraction);

        if (extraction.getMemory() == null) {
            return;
        }

        // 1. 写入 Markdown 总结
        if (!isBlank(extraction.getMemory().getMarkdownSummary())) {
            saveSummaryMd(
                    userId,
                    "overall",
                    extraction.getMemory().getMarkdownSummary());
        }

        // 2. 写入 RAG Chunk
        if (extraction.getMemory().getChunks() != null) {
            for (String chunk : extraction.getMemory().getChunks()) {
                if (isBlank(chunk)) {
                    continue;
                }

                saveChunk(
                        userId,
                        "interview",
                        sessionId,
                        chunk);
            }
        }

    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addMemoryTextSafely(Long userId, Long memoryChunkId, String content) {
        try {
            vectorStoreService.addMemoryText(userId, memoryChunkId, content);
        } catch (Exception e) {
            log.warn("写入向量库失败，已保留MySQL记忆记录：{}", e.getMessage());
        }
    }
}
