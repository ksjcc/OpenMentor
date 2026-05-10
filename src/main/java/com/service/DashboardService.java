package com.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.dashboard.DashboardDTO;
import com.dto.dashboard.RecentInterviewDTO;
import com.dto.dashboard.SkillRadarDTO;
import com.dto.dashboard.WeaknessRankDTO;
import com.entity.Flashcard;
import com.entity.InterviewSession;
import com.entity.MemoryProfileJson;
import com.entity.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.FlashcardMapper;
import com.mapper.InterviewSessionMapper;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.UserProfileMapper;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
  private final MemoryProfileJsonMapper memoryProfileJsonMapper;
  private final UserProfileMapper userProfileMapper;
  private final InterviewSessionMapper interviewSessionMapper;
  private final FlashcardMapper flashcardMapper;

  private final ObjectMapper objectMapper;

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

    // =========================
    // 5. 薄弱点排行
    // 第一版先静态
    // 后面从 extraction JSON 统计
    // =========================
    dashboard.setWeaknesses(
        buildWeaknesses(userId));

    return dashboard;
  }

  /**
   * 构建技能画像
   *
   */
  private List<SkillRadarDTO> buildSkills(Long userId) {

    List<SkillRadarDTO> list = new ArrayList<>();

    UserProfile userProfile = getUserProfile(userId);
    if (userProfile != null && !isBlank(userProfile.getAbilityMap())) {
      appendAbilityMapSkills(list, userProfile.getAbilityMap());
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
          dto.setScore(field.getValue().asInt(0));
          list.add(dto);
        });
        return list;
      }

      JsonNode techStack = root.get("techStack");
      if (techStack != null && techStack.isArray()) {

        for (JsonNode node : techStack) {

          SkillRadarDTO dto = new SkillRadarDTO();

          dto.setSkill(node.asText());

          // 第一版随机给分
          // 后面根据面试得分动态计算
          dto.setScore(60);

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

    List<RecentInterviewDTO> list = new ArrayList<>();

    for (InterviewSession session : sessions) {

      RecentInterviewDTO dto = new RecentInterviewDTO();

      dto.setSessionId(session.getId());
      dto.setInterviewType(session.getInterviewType());
      dto.setTargetRole(session.getTargetRole());
      dto.setStatus(session.getStatus());
      dto.setCreatedAt(session.getStartTime());

      list.add(dto);
    }

    return list;
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

    List<String> list = new ArrayList<>();

    list.add("建议加强 Redis 缓存一致性。");
    list.add("建议复习 JVM GC 与内存模型。");

    return list;
  }

  /**
   * 薄弱点排行
   *
   * 第一版先静态模拟
   * 后面从 KnowledgeExtractionResult 统计
   */
  private List<WeaknessRankDTO> buildWeaknesses(Long userId) {

    List<WeaknessRankDTO> list = new ArrayList<>();

    UserProfile profile = getUserProfile(userId);
    if (profile != null && !isBlank(profile.getWeaknesses())) {
      try {
        JsonNode weaknesses = objectMapper.readTree(profile.getWeaknesses());
        if (weaknesses.isArray()) {
          for (JsonNode node : weaknesses) {
            String name = node.path("name").asText("");
            if (isBlank(name)) {
              continue;
            }

            WeaknessRankDTO dto = new WeaknessRankDTO();
            dto.setName(name);
            dto.setCount(node.path("occurrenceCount").asInt(1));
            list.add(dto);
          }

          list.sort(Comparator.comparing(WeaknessRankDTO::getCount).reversed());
          return list;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    WeaknessRankDTO dto1 = new WeaknessRankDTO();

    dto1.setName("Redis缓存一致性");
    dto1.setCount(5);

    list.add(dto1);

    WeaknessRankDTO dto2 = new WeaknessRankDTO();

    dto2.setName("JVM GC");
    dto2.setCount(3);

    list.add(dto2);

    return list;
  }

  private void appendAbilityMapSkills(List<SkillRadarDTO> list, String abilityMapJson) {
    try {
      JsonNode abilityMap = objectMapper.readTree(abilityMapJson);
      if (!abilityMap.isObject()) {
        return;
      }

      abilityMap.fields().forEachRemaining(field -> {
        SkillRadarDTO dto = new SkillRadarDTO();
        dto.setSkill(field.getKey());
        dto.setScore(field.getValue().asInt(0));
        list.add(dto);
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
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
