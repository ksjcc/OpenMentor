package com.service.review;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.review.ReviewPracticeDTO;
import com.entity.Flashcard;
import com.entity.ReviewRecord;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.mapper.FlashcardMapper;
import com.mapper.KnowledgeNodeMapper;
import com.mapper.KnowledgeRelationMapper;
import com.mapper.ReviewRecordMapper;
import com.service.Dashboard.FlashcardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 复习训练：按天组织练习。
 *
 * <p>
 * 规则：
 * <ul>
 *   <li>一道闪卡如果 {@code next_review_time} 落在某天，它就属于那天的练习；</li>
 *   <li>逾期（早于今天）的卡片统一算进今天，避免永远补不回来；</li>
 *   <li>每次作答写一条 {@code review_record}（含 practice_date），当天的正确率/错误次数从这里统计；</li>
 *   <li>作答后卡片被排到未来的间隔日期，自然从当天的待复习列表里消失。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPracticeService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;
    private static final int RELATED_LIMIT = 12;

    private final FlashcardMapper flashcardMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final FlashcardService flashcardService;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;

    // ==================== 计划（按天） ====================

    /** 今天起 N 天的练习概览。 */
    public List<ReviewPracticeDTO.Daily> calendar(Long userId, Integer days) {
        requireUser(userId);
        int size = days == null || days <= 0 ? DEFAULT_DAYS : Math.min(days, MAX_DAYS);
        LocalDate today = LocalDate.now();

        List<Flashcard> allCards = listCards(userId);
        Map<LocalDate, List<ReviewRecord>> recordsByDate = loadRecordsGroupedByDate(userId);

        List<ReviewPracticeDTO.Daily> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            LocalDate date = today.plusDays(i);
            boolean isToday = i == 0;
            int remaining = (int) allCards.stream()
                    .filter(card -> dueDate(card).equals(date) || (isToday && dueDate(card).isBefore(today)))
                    .count();
            List<ReviewRecord> records = recordsByDate.getOrDefault(date, List.of());
            int correct = countResult(records, true);
            int wrong = records.size() - correct;

            ReviewPracticeDTO.Daily dto = new ReviewPracticeDTO.Daily();
            dto.setDate(date);
            dto.setDone(records.size());
            dto.setCorrect(correct);
            dto.setWrong(wrong);
            dto.setAccuracy(records.isEmpty() ? null : (double) correct / records.size());
            dto.setRemaining(remaining);
            dto.setTotal(records.size() + remaining);
            dto.setStatus(resolveStatus(date, today, records.size(), remaining));
            result.add(dto);
        }
        return result;
    }

    private String resolveStatus(LocalDate date, LocalDate today, int done, int remaining) {
        if (done == 0 && remaining == 0) {
            return "EMPTY";
        }
        if (remaining > 0) {
            return date.equals(today) ? "TODAY" : "UPCOMING";
        }
        return "DONE";
    }

    // ==================== 某一天的练习 ====================

    public ReviewPracticeDTO.Detail practice(Long userId, LocalDate date) {
        requireUser(userId);
        LocalDate target = date == null ? LocalDate.now() : date;
        LocalDate today = LocalDate.now();

        List<ReviewRecord> records = listRecords(userId, target);
        Set<Long> answeredCardIds = new LinkedHashSet<>();
        for (ReviewRecord record : records) {
            if (record.getFlashcardId() != null) {
                answeredCardIds.add(record.getFlashcardId());
            }
        }

        List<Flashcard> dueCards = listCards(userId).stream()
                .filter(card -> !answeredCardIds.contains(card.getId()))
                .filter(card -> {
                    LocalDate due = dueDate(card);
                    return due.equals(target) || (target.equals(today) && due.isBefore(today));
                })
                .sorted(Comparator.comparing(Flashcard::getNextReviewTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        int correct = countResult(records, true);
        int wrong = records.size() - correct;

        ReviewPracticeDTO.Detail detail = new ReviewPracticeDTO.Detail();
        detail.setDate(target);
        detail.setDone(records.size());
        detail.setCorrect(correct);
        detail.setWrong(wrong);
        detail.setRemaining(dueCards.size());
        detail.setTotal(records.size() + dueCards.size());
        detail.setAccuracy(records.isEmpty() ? null : (double) correct / records.size());
        detail.setFinished(dueCards.isEmpty() && !records.isEmpty());
        detail.setActionable(!target.isBefore(today));

        Map<Long, String> lastResult = latestResults(userId, target);
        List<ReviewPracticeDTO.Card> cards = new ArrayList<>();
        for (Flashcard card : dueCards) {
            cards.add(toCardDto(card, lastResult.get(card.getId())));
        }
        // 已经答过的也带上（回看当天练习时能看到全部题）
        for (ReviewRecord record : records) {
            Flashcard card = flashcardMapper.selectById(record.getFlashcardId());
            if (card == null) {
                continue;
            }
            cards.add(toCardDto(card, record.getResult()));
        }
        detail.setCards(cards);
        return detail;
    }

    private ReviewPracticeDTO.Card toCardDto(Flashcard card, String lastResult) {
        ReviewPracticeDTO.Card dto = new ReviewPracticeDTO.Card();
        dto.setCardId(String.valueOf(card.getId()));
        dto.setQuestion(card.getQuestion());
        dto.setAnswer(card.getAnswer());
        dto.setTopic(isBlank(card.getTopic()) ? "知识点" : card.getTopic());
        dto.setDifficulty(card.getDifficulty());
        dto.setMasteryLevel(card.getMasteryLevel());
        dto.setLastResult(lastResult);
        return dto;
    }

    // ==================== 作答 ====================

    @Transactional(rollbackFor = Exception.class)
    public ReviewPracticeDTO.SubmitResult submit(Long userId, Long cardId, boolean correct) {
        requireUser(userId);
        if (cardId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "卡片ID不能为空");
        }

        Flashcard card = flashcardMapper.selectById(cardId);
        if (card == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "卡片不存在");
        }
        if (card.getUserId() != null && !card.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权复习这张卡片");
        }

        LocalDate today = LocalDate.now();
        LocalDate due = dueDate(card);
        // 逾期卡片算进今天的练习
        LocalDate practiceDate = due.isBefore(today) ? today : due;

        Flashcard updated = flashcardService.applyReview(cardId, correct);
        int intervalDays = updated == null || updated.getNextReviewTime() == null
                ? 1
                : Math.max(1, (int) (updated.getNextReviewTime().toLocalDate().toEpochDay()
                        - today.toEpochDay()));

        ReviewRecord record = new ReviewRecord();
        record.setFlashcardId(cardId);
        record.setUserId(userId);
        record.setResult(correct ? "CORRECT" : "WRONG");
        record.setTopic(card.getTopic());
        record.setPracticeDate(practiceDate);
        record.setIntervalDays(intervalDays);
        record.setReviewedAt(LocalDateTime.now());
        reviewRecordMapper.insert(record);

        ReviewPracticeDTO.Detail after = practice(userId, practiceDate);
        ReviewPracticeDTO.SubmitResult result = new ReviewPracticeDTO.SubmitResult();
        result.setCardId(String.valueOf(cardId));
        result.setCorrect(correct);
        result.setDone(after.getDone());
        result.setCorrectCount(after.getCorrect());
        result.setWrongCount(after.getWrong());
        result.setAccuracy(after.getAccuracy());
        result.setRemaining(after.getRemaining());
        result.setFinished(after.getRemaining() == 0);
        result.setNextReviewTime(updated == null ? null : updated.getNextReviewTime());
        return result;
    }

    // ==================== 关联知识点 ====================

    /**
     * 「关联知识点」弹窗：同主题的历史闪卡 + 知识图谱里与该主题相连的其它主题的闪卡。
     */
    public ReviewPracticeDTO.RelatedGroup related(Long userId, Long cardId) {
        requireUser(userId);
        if (cardId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "卡片ID不能为空");
        }
        Flashcard card = flashcardMapper.selectById(cardId);
        if (card == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "卡片不存在");
        }

        String rawTopic = card.getTopic() == null ? "" : card.getTopic().trim();
        String topic = rawTopic.isEmpty() ? "知识点" : rawTopic;
        List<String> linkedTopics = rawTopic.isEmpty() ? List.of() : graphNeighborTopics(userId, rawTopic);

        ReviewPracticeDTO.RelatedGroup group = new ReviewPracticeDTO.RelatedGroup();
        group.setTopic(topic);
        group.setLinkTopics(linkedTopics);

        Map<Long, String> lastResults = latestResults(userId, null);
        List<ReviewPracticeDTO.Related> sameTopic = new ArrayList<>();
        List<ReviewPracticeDTO.Related> graphTopic = new ArrayList<>();

        for (Flashcard other : listCards(userId)) {
            if (other.getId().equals(cardId)) {
                continue;
            }
            String otherTopic = other.getTopic() == null ? "" : other.getTopic().trim();
            if (rawTopic.equals(otherTopic)) {
                sameTopic.add(toRelated(other, lastResults.get(other.getId()), "SAME_TOPIC"));
            } else if (linkedTopics.contains(otherTopic)) {
                graphTopic.add(toRelated(other, lastResults.get(other.getId()), "GRAPH"));
            }
        }

        List<ReviewPracticeDTO.Related> merged = new ArrayList<>();
        merged.addAll(sameTopic);
        merged.addAll(graphTopic);
        group.setCards(merged.stream().limit(RELATED_LIMIT).toList());
        return group;
    }

    private ReviewPracticeDTO.Related toRelated(Flashcard card, String lastResult, String reason) {
        ReviewPracticeDTO.Related dto = new ReviewPracticeDTO.Related();
        dto.setCardId(String.valueOf(card.getId()));
        dto.setQuestion(card.getQuestion());
        dto.setAnswer(card.getAnswer());
        dto.setTopic(isBlank(card.getTopic()) ? "知识点" : card.getTopic());
        dto.setMasteryLevel(card.getMasteryLevel());
        dto.setCreatedAt(card.getCreatedAt());
        dto.setLastResult(lastResult);
        dto.setReason(reason);
        return dto;
    }

    /** 知识图谱里与当前主题相连的其它实体名（一跳邻居）。 */
    private List<String> graphNeighborTopics(Long userId, String topic) {
        try {
            List<GraphRagEntity> entities = knowledgeNodeMapper.selectList(
                    new LambdaQueryWrapper<GraphRagEntity>().eq(GraphRagEntity::getUserId, userId));
            if (entities == null || entities.isEmpty()) {
                return List.of();
            }
            Map<Long, String> nameById = new LinkedHashMap<>();
            Long matchedId = null;
            for (GraphRagEntity entity : entities) {
                nameById.put(entity.getId(), entity.getName());
                if (matchedId == null && topic.equalsIgnoreCase(entity.getName())) {
                    matchedId = entity.getId();
                }
            }
            if (matchedId == null) {
                return List.of();
            }

            List<GraphRagRelationship> relations = knowledgeRelationMapper.selectList(
                    new LambdaQueryWrapper<GraphRagRelationship>().eq(GraphRagRelationship::getUserId, userId));
            Set<String> neighbors = new LinkedHashSet<>();
            Long source = matchedId;
            for (GraphRagRelationship relation : relations) {
                if (source.equals(relation.getSourceEntityId())) {
                    addIfPresent(neighbors, nameById.get(relation.getTargetEntityId()), topic);
                } else if (source.equals(relation.getTargetEntityId())) {
                    addIfPresent(neighbors, nameById.get(relation.getSourceEntityId()), topic);
                }
            }
            return new ArrayList<>(neighbors);
        } catch (Exception ex) {
            log.warn("读取知识图谱邻居失败，关联知识点降级为同主题 userId={} topic={}", userId, topic, ex);
            return List.of();
        }
    }

    private void addIfPresent(Set<String> neighbors, String name, String self) {
        if (!isBlank(name) && !name.equalsIgnoreCase(self)) {
            neighbors.add(name.trim());
        }
    }

    // ==================== 公共查询 ====================

    private List<Flashcard> listCards(Long userId) {
        List<Flashcard> cards = flashcardMapper.selectList(new LambdaQueryWrapper<Flashcard>()
                .eq(Flashcard::getUserId, userId)
                .orderByAsc(Flashcard::getNextReviewTime));
        return cards == null ? List.of() : cards;
    }

    /** 卡片计划复习日；没有排期时按创建日兜底。 */
    private LocalDate dueDate(Flashcard card) {
        if (card.getNextReviewTime() != null) {
            return card.getNextReviewTime().toLocalDate();
        }
        if (card.getCreatedAt() != null) {
            return card.getCreatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private List<ReviewRecord> listRecords(Long userId, LocalDate date) {
        if (date == null) {
            return reviewRecordMapper.selectList(new LambdaQueryWrapper<ReviewRecord>()
                    .eq(ReviewRecord::getUserId, userId)
                    .orderByAsc(ReviewRecord::getId));
        }
        return reviewRecordMapper.selectList(new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getUserId, userId)
                .eq(ReviewRecord::getPracticeDate, date)
                .orderByAsc(ReviewRecord::getId));
    }

    private Map<LocalDate, List<ReviewRecord>> loadRecordsGroupedByDate(Long userId) {
        Map<LocalDate, List<ReviewRecord>> grouped = new LinkedHashMap<>();
        for (ReviewRecord record : listRecords(userId, null)) {
            LocalDate date = record.getPracticeDate() == null
                    ? (record.getReviewedAt() == null ? LocalDate.now() : record.getReviewedAt().toLocalDate())
                    : record.getPracticeDate();
            grouped.computeIfAbsent(date, key -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    private Map<Long, String> latestResults(Long userId, LocalDate date) {
        Map<Long, String> latest = new LinkedHashMap<>();
        for (ReviewRecord record : listRecords(userId, date)) {
            if (record.getFlashcardId() != null) {
                latest.put(record.getFlashcardId(), record.getResult());
            }
        }
        return latest;
    }

    private int countResult(List<ReviewRecord> records, boolean correct) {
        int count = 0;
        for (ReviewRecord record : records) {
            boolean isCorrect = "CORRECT".equalsIgnoreCase(record.getResult());
            if (isCorrect == correct) {
                count++;
            }
        }
        return count;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
