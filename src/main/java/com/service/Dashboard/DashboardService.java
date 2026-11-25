package com.service.Dashboard;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.dashboard.DashboardDTO;
import com.dto.dashboard.LearningRecommendationDTO;
import com.dto.dashboard.RecentInterviewDTO;
import com.dto.dashboard.SkillRadarDTO;
import com.dto.dashboard.WeaknessRankDTO;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.WeaknessDTO;
import com.entity.Flashcard;
import com.entity.InterviewMessage;
import com.entity.InterviewSession;
import com.entity.MemoryProfileJson;
import com.entity.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.FlashcardMapper;
import com.mapper.InterviewMessageMapper;
import com.mapper.InterviewSessionMapper;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.UserProfileMapper;
import com.service.JsonParseService;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 服务
 *
 * 负责聚合：
 * 1. 用户能力画像
 * 2. 薄弱点排行
 * 3. 最近面试
 * 4. 今日复习
 * 5. AI建议
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
  private static final String STATUS_FINISHED = "FINISHED";
  private static final int MAX_RECENT_SUMMARIES = 10;
  private static final int TREND_WINDOW = 14;
  private static final int RATING_WINDOW = 100;

  private final MemoryProfileJsonMapper memoryProfileJsonMapper;
  private final UserProfileMapper userProfileMapper;
  private final InterviewSessionMapper interviewSessionMapper;
  private final InterviewMessageMapper interviewMessageMapper;
  private final FlashcardMapper flashcardMapper;
  private final LearningRecommendationService learningRecommendationService;
  private final ObjectMapper objectMapper;
  private final JsonParseService jsonParseService;

  /**
   * 获取 Dashboard 数据
   */
  public DashboardDTO getDashboard(Long userId) {

    DashboardDTO dashboard = new DashboardDTO();
    dashboard.setSkills(buildSkills(userId));
    dashboard.setRecentInterviews(buildRecentInterviews(userId));
    dashboard.setTodayReviewCount(
        countTodayReviews(userId));

    // =========================
    // 4. AI建议
    // 第一版先简单写
    // 后面可以 Agent 化
    // =========================
    dashboard.setAiSuggestions(
        buildSuggestions(userId));
    dashboard.setLearningRecommendation(
        learningRecommendationService.generate(userId));

    // =========================
    // 5. 薄弱点排行
    // 第一版先静态
    // 后面从 extraction JSON 统计
    // =========================
    dashboard.setWeaknesses(
        buildWeaknesses(userId));

    // =========================
    // 6. 总览 KPI 与得分趋势（全部来自真实 session）
    // =========================
    List<InterviewSession> rated = getRatedSessions(userId);
    dashboard.setTotalSessions(countSessions(userId));
    dashboard.setAverageScore(averageOf(rated));
    dashboard.setScoreTrend(rated.stream().map(InterviewSession::getScore).toList());
    dashboard.setTrendLabels(rated.stream().map(this::trendLabel).toList());

    return dashboard;
  }

  private Integer countSessions(Long userId) {
    Long count = interviewSessionMapper.selectCount(
        new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId));
    return count == null ? 0 : count.intValue();
  }

  /** 最近有评分的场次，按时间升序，用于画趋势。 */
  private List<InterviewSession> getRatedSessions(Long userId) {
    List<InterviewSession> sessions = interviewSessionMapper.selectList(
        new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .isNotNull(InterviewSession::getScore)
            .orderByDesc(InterviewSession::getId)
            .last("LIMIT " + RATING_WINDOW));
    if (sessions == null || sessions.isEmpty()) {
      return List.of();
    }
    List<InterviewSession> ordered = new ArrayList<>(sessions);
    ordered.sort(Comparator.comparing(InterviewSession::getId));
    if (ordered.size() > TREND_WINDOW) {
      return ordered.subList(ordered.size() - TREND_WINDOW, ordered.size());
    }
    return ordered;
  }

  private Integer averageOf(List<InterviewSession> sessions) {
    if (sessions == null || sessions.isEmpty()) {
      return null;
    }
    int sum = 0;
    int count = 0;
    for (InterviewSession session : sessions) {
      if (session.getScore() != null) {
        sum += session.getScore();
        count++;
      }
    }
    return count == 0 ? null : Math.round((float) sum / count);
  }

  private String trendLabel(InterviewSession session) {
    LocalDateTime time = session.getEndedAt() != null ? session.getEndedAt() : session.getStartTime();
    if (time == null) {
      return "";
    }
    return "%02d-%02d".formatted(time.getMonthValue(), time.getDayOfMonth());
  }

  /**
   * 构建技能画像
   *
   */
  private List<SkillRadarDTO> buildSkills(Long userId) {

    List<SkillRadarDTO> list = new ArrayList<>();
    Map<String, Integer> weaknessPenalty = buildWeaknessPenaltyMap(userId);

    UserProfile userProfile = getUserProfile(userId);
    if (userProfile != null && !isBlank(userProfile.getAbilityMap())) {
      appendAbilityMapSkills(list, userProfile.getAbilityMap(), weaknessPenalty);
      if (!list.isEmpty()) {
        return list;
      }
    }

    MemoryProfileJson profile = memoryProfileJsonMapper.selectOne(
        new LambdaQueryWrapper<MemoryProfileJson>()
            .eq(MemoryProfileJson::getUserId, userId)
            .orderByDesc(MemoryProfileJson::getId)
            .last("LIMIT 1"));

    if (profile == null) {
      return list;
    }

    try {

      JsonNode root = objectMapper.readTree(profile.getProfileData());

      JsonNode abilityMap = root.get("abilityMap");
      if (abilityMap != null && abilityMap.isObject()) {
        abilityMap.fields().forEachRemaining(field -> {
          SkillRadarDTO dto = new SkillRadarDTO();
          dto.setSkill(field.getKey());
          dto.setScore(applyWeaknessPenalty(field.getKey(), field.getValue().asInt(0), weaknessPenalty));
          list.add(dto);
        });
        return list;
      }

      JsonNode techStack = root.get("techStack");
      if (techStack != null && techStack.isArray()) {

        for (JsonNode node : techStack) {

          SkillRadarDTO dto = new SkillRadarDTO();
          String skill = node.asText();

          dto.setSkill(skill);
          dto.setScore(inferTechStackScore(skill, weaknessPenalty));

          list.add(dto);
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return list;
  }

  /**
   * 最近面试记录
   */
  private List<RecentInterviewDTO> buildRecentInterviews(Long userId) {

    List<InterviewSession> sessions = interviewSessionMapper.selectList(
        new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .orderByDesc(InterviewSession::getId)
            .last("LIMIT 5"));

    if (sessions == null || sessions.isEmpty()) {
      return List.of();
    }

    // 一次查出这几场的消息，统计每场的提问轮次
    List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
    List<InterviewMessage> messages = interviewMessageMapper.selectList(
        new LambdaQueryWrapper<InterviewMessage>()
            .in(InterviewMessage::getSessionId, sessionIds)
            .eq(InterviewMessage::getRole, "ai"));
    Map<Long, Integer> turns = new LinkedHashMap<>();
    if (messages != null) {
      for (InterviewMessage message : messages) {
        turns.merge(message.getSessionId(), 1, Integer::sum);
      }
    }

    List<RecentInterviewDTO> list = new ArrayList<>();

    for (InterviewSession session : sessions) {

      RecentInterviewDTO dto = new RecentInterviewDTO();

      dto.setSessionId(String.valueOf(session.getId()));
      dto.setInterviewType(session.getInterviewType());
      dto.setTargetRole(session.getTargetRole());
      dto.setStatus(session.getStatus());
      dto.setTopic(session.getTopic());
      dto.setScore(session.getScore());
      dto.setTurns(turns.getOrDefault(session.getId(), 0));
      dto.setTopWeakness(firstWeaknessOf(session.getSummary()));
      dto.setCreatedAt(session.getStartTime());
      dto.setEndedAt(session.getEndedAt());

      list.add(dto);
    }

    return list;
  }

  private String firstWeaknessOf(String summaryJson) {
    if (isBlank(summaryJson)) {
      return null;
    }
    try {
      KnowledgeExtractionResult extraction = jsonParseService.parseExtractionLenient(summaryJson);
      if (extraction.getWeaknesses() == null || extraction.getWeaknesses().isEmpty()) {
        return null;
      }
      WeaknessDTO weakness = extraction.getWeaknesses().get(0);
      return weakness == null || isBlank(weakness.getName()) ? null : weakness.getName().trim();
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * 今日待复习数量
   */
  private Integer countTodayReviews(Long userId) {
    Long count = flashcardMapper.selectCount(
        new LambdaQueryWrapper<Flashcard>()
            .eq(Flashcard::getUserId, userId)
            .le(
                Flashcard::getNextReviewTime,
                LocalDateTime.now()));

    return count.intValue();
  }

  /**
   * AI建议
   */
  private List<String> buildSuggestions(Long userId) {
    LearningRecommendationDTO recommendation = learningRecommendationService.generate(userId);
    List<String> suggestions = new ArrayList<>();

    if (recommendation == null) {
      suggestions.add("继续完成至少一场模拟面试，系统会基于真实答题表现生成成长建议。");
      return suggestions;
    }

    if (recommendation.getRoleGapSuggestions() != null) {
      suggestions.addAll(recommendation.getRoleGapSuggestions().stream()
          .filter(s -> !isBlank(s))
          .map(String::trim)
          .toList());
    }

    if (recommendation.getWeakTopics() != null && !recommendation.getWeakTopics().isEmpty()) {
      String weakTopicSummary = String.join("、", recommendation.getWeakTopics());
      suggestions.add("本周优先复习 " + weakTopicSummary + "，按照建议顺序逐个补齐核心知识点。");
    }

    if (recommendation.getFlashcards() != null && !recommendation.getFlashcards().isEmpty()) {
      suggestions.add("结合系统生成的知识闪卡进行复盘，优先完成高频薄弱点对应的题目记忆与表达训练。");
    }

    if (suggestions.isEmpty()) {
      suggestions.add("继续完成至少一场模拟面试，系统会基于真实答题表现生成成长建议。");
    }

    return suggestions.stream()
        .filter(s -> !isBlank(s))
        .map(String::trim)
        .distinct()
        .limit(3)
        .toList();
  }

  /**
   * 薄弱点排行
   *
   * 第一版先静态模拟
   * 后面从 KnowledgeExtractionResult 统计
   */
  private List<WeaknessRankDTO> buildWeaknesses(Long userId) {

    Map<String, Integer> aggregated = new LinkedHashMap<>();

    UserProfile profile = getUserProfile(userId);
    if (profile != null && !isBlank(profile.getWeaknesses())) {
      appendWeaknessesFromProfile(aggregated, profile.getWeaknesses());
    }

    for (InterviewSession session : getRecentFinishedSessions(userId, MAX_RECENT_SUMMARIES)) {
      appendWeaknessesFromSummary(aggregated, session.getSummary());
    }

    return aggregated.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
            .thenComparing(Map.Entry::getKey))
        .limit(5)
        .map(entry -> {
          WeaknessRankDTO dto = new WeaknessRankDTO();
          dto.setName(entry.getKey());
          dto.setCount(entry.getValue());
          return dto;
        })
        .toList();
  }

  private void appendAbilityMapSkills(
      List<SkillRadarDTO> list,
      String abilityMapJson,
      Map<String, Integer> weaknessPenalty) {
    try {
      JsonNode abilityMap = objectMapper.readTree(abilityMapJson);
      if (!abilityMap.isObject()) {
        return;
      }

      abilityMap.fields().forEachRemaining(field -> {
        SkillRadarDTO dto = new SkillRadarDTO();
        dto.setSkill(field.getKey());
        dto.setScore(applyWeaknessPenalty(field.getKey(), field.getValue().asInt(0), weaknessPenalty));
        list.add(dto);
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void appendWeaknessesFromProfile(Map<String, Integer> aggregated, String weaknessesJson) {
    try {
      JsonNode weaknesses = objectMapper.readTree(weaknessesJson);
      if (!weaknesses.isArray()) {
        return;
      }

      for (JsonNode node : weaknesses) {
        String name = normalizeWeaknessName(node.path("name").asText(""));
        if (name == null) {
          continue;
        }
        int count = node.path("occurrenceCount").asInt(1);
        aggregated.merge(name, Math.max(1, count), Integer::sum);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void appendWeaknessesFromSummary(Map<String, Integer> aggregated, String summaryJson) {
    if (isBlank(summaryJson)) {
      return;
    }

    try {
      KnowledgeExtractionResult extraction = jsonParseService.parseExtractionLenient(summaryJson);
      if (extraction.getWeaknesses() == null) {
        return;
      }

      for (WeaknessDTO weakness : extraction.getWeaknesses()) {
        String name = normalizeWeaknessName(weakness.getName());
        if (name == null) {
          continue;
        }
        int weight = weakness.getLevel() == null ? 1 : Math.max(1, weakness.getLevel());
        aggregated.merge(name, weight, Integer::sum);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Map<String, Integer> buildWeaknessPenaltyMap(Long userId) {
    Map<String, Integer> penalty = new LinkedHashMap<>();
    for (WeaknessRankDTO weakness : buildWeaknesses(userId)) {
      if (weakness == null || isBlank(weakness.getName())) {
        continue;
      }
      penalty.put(weakness.getName(), weakness.getCount());
    }
    return penalty;
  }

  private int applyWeaknessPenalty(String skill, int baseScore, Map<String, Integer> weaknessPenalty) {
    int score = clampScore(baseScore);
    if (isBlank(skill) || weaknessPenalty.isEmpty()) {
      return score;
    }

    int penalty = 0;
    for (Map.Entry<String, Integer> entry : weaknessPenalty.entrySet()) {
      if (skill.contains(entry.getKey()) || entry.getKey().contains(skill)) {
        penalty = Math.max(penalty, Math.min(entry.getValue() * 5, 25));
      }
    }
    return clampScore(score - penalty);
  }

  private int inferTechStackScore(String skill, Map<String, Integer> weaknessPenalty) {
    return applyWeaknessPenalty(skill, 75, weaknessPenalty);
  }

  private int clampScore(int score) {
    return Math.max(0, Math.min(100, score));
  }

  private String normalizeWeaknessName(String name) {
    if (isBlank(name)) {
      return null;
    }
    return name.trim();
  }

  private List<InterviewSession> getRecentFinishedSessions(Long userId, int limit) {
    return interviewSessionMapper.selectList(
        new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .eq(InterviewSession::getStatus, STATUS_FINISHED)
            .orderByDesc(InterviewSession::getId)
            .last("LIMIT " + limit));
  }

  private String firstNonBlank(String value, String fallback) {
    return isBlank(value) ? fallback : value;
  }

  private UserProfile getUserProfile(Long userId) {
    return userProfileMapper.selectOne(
        new LambdaQueryWrapper<UserProfile>()
            .eq(UserProfile::getUserId, userId)
            .last("LIMIT 1"));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

}
