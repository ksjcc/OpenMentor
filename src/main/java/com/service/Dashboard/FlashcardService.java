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
        createFlashcard(userId, question, answer, difficulty, null);
    }

    public void createFlashcard(Long userId, String question, String answer, Integer difficulty, String topic) {
        Flashcard card = new Flashcard();
        card.setUserId(userId);
        card.setQuestion(question);
        card.setAnswer(answer);
        card.setTopic(trimToNull(topic));

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
        applyReview(cardId, correct);
    }

    /**
     * 应用一次作答：更新掌握度与下次复习时间，返回更新后的卡片（卡片不存在返回 null）。
     */
    public Flashcard applyReview(Long cardId, boolean correct) {
        if (cardId == null) {
            return null;
        }
        Flashcard card = flashcardMapper.selectById(cardId);
        if (card == null) {
            return null;
        }

        int mastery = card.getMasteryLevel() == null ? 0 : card.getMasteryLevel();
        mastery += correct ? 20 : -10;
        mastery = Math.max(0, Math.min(100, mastery));
        card.setMasteryLevel(mastery);

        int intervalDays = intervalDaysFor(mastery);
        card.setNextReviewTime(LocalDateTime.now().plusDays(intervalDays));
        flashcardMapper.updateById(card);
        return card;
    }

    /** 掌握度 → 下次复习间隔（天）。 */
    public int intervalDaysFor(int mastery) {
        if (mastery < 30) {
            return 1;
        }
        if (mastery < 60) {
            return 3;
        }
        return mastery < 80 ? 7 : 15;
    }

    /** 生成闪卡；topic 用于把同一场面试的卡归到同一个知识点主题下。 */
    public void generateFlashcardsFromExtraction(Long userId, KnowledgeExtractionResult extraction) {
        generateFlashcardsFromExtraction(userId, extraction, null);
    }

    public void generateFlashcardsFromExtraction(Long userId, KnowledgeExtractionResult extraction, String topic) {
        if (extraction == null || extraction.getFlashcards() == null || extraction.getFlashcards().isEmpty()) {
            return;
        }

        String effectiveTopic = isBlank(topic) ? firstWeaknessName(extraction) : topic.trim();

        for (FlashcardDTO card : extraction.getFlashcards()) {
            if (card == null || isBlank(card.getQuestion()) || isBlank(card.getAnswer())) {
                continue;
            }
            // 同一道题不重复入库（重复复盘时会产生一模一样的卡片）
            if (existsQuestion(userId, card.getQuestion())) {
                continue;
            }

            createFlashcard(
                    userId,
                    card.getQuestion(),
                    card.getAnswer(),
                    card.getDifficulty(),
                    effectiveTopic);
        }
    }

    private boolean existsQuestion(Long userId, String question) {
        Long count = flashcardMapper.selectCount(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .eq(Flashcard::getQuestion, question));
        return count != null && count > 0;
    }

    private String firstWeaknessName(KnowledgeExtractionResult extraction) {
        if (extraction.getWeaknesses() == null || extraction.getWeaknesses().isEmpty()) {
            return "面试复盘";
        }
        var weakness = extraction.getWeaknesses().get(0);
        return weakness == null || isBlank(weakness.getName()) ? "面试复盘" : weakness.getName().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean containsTopic(Flashcard card, String normalizedTopic) {
        return containsText(card.getTopic(), normalizedTopic)
                || containsText(card.getQuestion(), normalizedTopic)
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
