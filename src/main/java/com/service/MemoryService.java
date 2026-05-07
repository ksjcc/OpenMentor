package com.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.MemorySummaryMdMapper;
import com.mapper.FlashcardMapper;
import com.mapper.MemoryChunkMapper;
import com.entity.MemoryProfileJson;
import com.entity.MemorySummaryMd;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.entity.Flashcard;
import com.entity.MemoryChunk;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryService {
    private final MemoryProfileJsonMapper memoryProfileJsonMapper;
    private final MemorySummaryMdMapper memorySummaryMdMapper;
    private final MemoryChunkMapper memoryChunkMapper;
    private final FlashcardMapper flashcardMapper;

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
            memory.setVersion(old.getVersion() + 1);
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

    /**
     * 保存语义记忆chunk
     */
    public void saveChunk(Long userId, String sourceType, Long sourceId, String content) {
        MemoryChunk chunk = new MemoryChunk();
        chunk.setUserId(userId);
        chunk.setSourceType(sourceType);
        chunk.setSourceId(sourceId);
        chunk.setContent(content);
        chunk.setEmbedding(null); // 第一版先不做 embedding
        memoryChunkMapper.insert(chunk);
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
     * 从提炼结果中生成闪卡（简单版：直接解析字符串）
     * 
     * @throws JsonProcessingException
     * @throws JsonMappingException
     */
    public void generateFlashcardsFromExtraction(Long userId, String extractionJson)
            throws JsonMappingException, JsonProcessingException {
        if (extractionJson == null || extractionJson.isEmpty()) {
            return;
        }
        // 1. 解析 JSON 字符串
        JsonNode root = new ObjectMapper().readTree(extractionJson);
        JsonNode flashcardsNode = root.path("flashcards");
        if (!flashcardsNode.isArray()) {
            return;
        }
        // 2. 遍历每个闪卡项
        for (JsonNode cardNode : flashcardsNode) {
            String question = cardNode.path("question").asText();
            String answer = cardNode.path("answer").asText();
            // 3. 保存到数据库
            Flashcard flashcard = new Flashcard();
            flashcard.setUserId(userId);
            flashcard.setQuestion(question);
            flashcard.setAnswer(answer);
            flashcard.setMasteryLevel(0); // 初始熟练度设为 0
            flashcard.setNextReviewTime(LocalDateTime.now()); // 初始下次复习时间设为当前时间
            flashcardMapper.insert(flashcard);
        }
    }
}
