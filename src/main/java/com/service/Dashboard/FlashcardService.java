package com.service.Dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.extraction.FlashcardDTO;
import com.dto.extraction.KnowledgeExtractionResult;
import com.entity.Flashcard;
import com.mapper.FlashcardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 闪卡服务
 * 负责：创建闪卡、查询复习任务、更新复习结果
 */
@Service
@RequiredArgsConstructor
public class FlashcardService {
    private final FlashcardMapper flashcardMapper;

    public void createFlashcard(Long userId, String question, String answer, Integer difficulty) {
        Flashcard card = new Flashcard();
        card.setUserId(userId);
        card.setQuestion(question);
        card.setAnswer(answer);

        card.setDifficulty(difficulty == null ? 2 : difficulty);

        // 初始掌握度
        card.setMasteryLevel(0);

        // 第一次复习：明天
        card.setNextReviewTime(LocalDateTime.now().plusDays(1));

        card.setCreatedAt(LocalDateTime.now());

        flashcardMapper.insert(card);
    }

    /**
     * 批量创建闪卡（从AI提炼结果）
     */
    public void batchCreate(Long userId, List<Flashcard> cards) {
        for (Flashcard c : cards) {
            c.setUserId(userId);
            c.setMasteryLevel(0);
            c.setNextReviewTime(LocalDateTime.now().plusDays(1));
            c.setCreatedAt(LocalDateTime.now());
            flashcardMapper.insert(c);
        }
    }

    /**
     * 获取“今天需要复习”的卡片
     */
    public List<Flashcard> getTodayReview(Long userId) {
        /**
         * 1. 筛选出用户ID匹配且复习时间小于等于当前时间的卡片
         * 2. 按复习时间排序，确保先复习到期的卡片
         */
        return flashcardMapper.selectList(
                new LambdaQueryWrapper<Flashcard>()
                        .eq(Flashcard::getUserId, userId)
                        .le(Flashcard::getNextReviewTime, LocalDateTime.now())
                        .orderByAsc(Flashcard::getNextReviewTime));
    }

    public List<FlashcardDTO> findByTopic(Long userId, String topic) {
        if (isBlank(topic)) {
            return List.of();
        }

        String normalizedTopic = topic.trim().toLowerCase(Locale.ROOT);

        return flashcardMapper.selectList(
                new LambdaQueryWrapper<Flashcard>()
                        .eq(Flashcard::getUserId, userId)
                        .orderByDesc(Flashcard::getId)
                        .last("LIMIT 50"))
                .stream()
                .filter(card -> containsTopic(card, normalizedTopic))
                .map(this::toDto)
                .limit(5)
                .toList();
    }

    /**
     * 用户完成复习后更新状态（简单版间隔重复）
     */
    public void review(Long cardId, boolean correct) {

        Flashcard card = flashcardMapper.selectById(cardId);

        if (card == null)
            return;

        int mastery = card.getMasteryLevel();

        if (correct) {
            mastery += 20;
        } else {
            mastery -= 10;
        }

        mastery = Math.max(0, Math.min(100, mastery));

        card.setMasteryLevel(mastery);

        // 简单间隔算法
        int intervalDays;
        if (mastery < 30)
            intervalDays = 1;
        else if (mastery < 60)
            intervalDays = 3;
        else if (mastery < 80)
            intervalDays = 7;
        else
            intervalDays = 15;

        card.setNextReviewTime(LocalDateTime.now().plusDays(intervalDays));

        flashcardMapper.updateById(card);
    }

    public void generateFlashcardsFromExtraction(Long userId, KnowledgeExtractionResult extraction) {
        if (extraction == null || extraction.getFlashcards() == null || extraction.getFlashcards().isEmpty()) {
            return;
        }

        for (FlashcardDTO card : extraction.getFlashcards()) {
            if (card == null || isBlank(card.getQuestion()) || isBlank(card.getAnswer())) {
                continue;
            }

            createFlashcard(
                    userId,
                    card.getQuestion(),
                    card.getAnswer(),
                    card.getDifficulty());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsTopic(Flashcard card, String normalizedTopic) {
        return containsText(card.getQuestion(), normalizedTopic)
                || containsText(card.getAnswer(), normalizedTopic);
    }

    private boolean containsText(String text, String normalizedTopic) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(normalizedTopic);
    }

    private FlashcardDTO toDto(Flashcard card) {
        FlashcardDTO dto = new FlashcardDTO();
        dto.setQuestion(card.getQuestion());
        dto.setAnswer(card.getAnswer());
        dto.setDifficulty(card.getDifficulty());
        return dto;
    }
}
