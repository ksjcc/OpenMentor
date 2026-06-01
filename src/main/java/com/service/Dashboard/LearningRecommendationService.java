package com.service.Dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ai.agent.ReviewPlannerAgent;
import com.ai.client.LlmClient;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.dashboard.LearningPlanDTO;
import com.dto.dashboard.LearningRecommendationDTO;
import com.dto.extraction.FlashcardDTO;
import com.entity.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.UserProfileMapper;
import com.service.JsonParseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningRecommendationService {
  private final UserProfileMapper userProfileMapper;
  private final FlashcardService flashcardService;
  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;
  private final PromptBuilder promptBuilder;
  private final ReviewPlannerAgent reviewPlannerAgent;
  private final JsonParseService jsonParseService;

  public LearningRecommendationDTO generate(Long userId) {
    UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
        .eq(UserProfile::getUserId, userId)
        .last("LIMIT 1"));
    if (profile == null) {
      return LearningRecommendationDTO.builder()
          .weakTopics(List.of())
          .reviewOrder(List.of())
          .flashcards(List.of())
          .roleGapSuggestions(List.of())
          .build();
    }
    Map<String, Integer> abilityMap = parseAbilityMap(profile.getAbilityMap());
    List<String> weakTopics = abilityMap.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByValue())
        .limit(3)
        .map(Map.Entry::getKey)
        .toList();
    List<String> reviewOrder = new ArrayList<>(weakTopics);
    List<FlashcardDTO> flashcards = weakTopics.stream()
        .flatMap(topic -> flashcardService.findByTopic(userId, topic).stream())
        .limit(10)
        .toList();
    List<String> roleGapSuggestions = generateRoleGapSuggestions(
        profile.getTargetRole(),
        weakTopics);
    LearningPlanDTO learningPlan = generateLearningPlan(
        profile,
        weakTopics,
        reviewOrder,
        flashcards);
    return LearningRecommendationDTO.builder()
        .weakTopics(weakTopics)
        .reviewOrder(reviewOrder)
        .flashcards(flashcards)
        .roleGapSuggestions(roleGapSuggestions)
        .learningPlan(learningPlan)
        .build();
  }

  private Map<String, Integer> parseAbilityMap(String abilityMapJson) {
    if (abilityMapJson == null || abilityMapJson.isBlank()) {
      return Collections.emptyMap();
    }

    try {
      return objectMapper.readValue(
          abilityMapJson,
          new TypeReference<Map<String, Integer>>() {
          });
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  private List<String> generateRoleGapSuggestions(
      String targetRole,
      List<String> weakTopics) {
    if (weakTopics.isEmpty()) {
      return List.of();
    }

    PromptContext context = PromptContext.builder()
        .targetRole(targetRole)
        .recommends(weakTopics)
        .build();

    String prompt = String.join("\n", promptBuilder.buildRecommendsPrompt(context));

    try {
      String result = llmClient.chat(prompt);

      return Arrays.stream(result.split("\n"))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .limit(3)
          .toList();
    } catch (Exception e) {
      return weakTopics.stream()
          .map(t -> "建议优先复习 " + t)
          .limit(3)
          .toList();
    }
  }

  private LearningPlanDTO generateLearningPlan(
      UserProfile profile,
      List<String> weakTopics,
      List<String> reviewOrder,
      List<FlashcardDTO> flashcards) {
    if (profile == null || weakTopics.isEmpty()) {
      return buildFallbackPlan(weakTopics, flashcards);
    }

    List<String> flashcardPoints = flashcards.stream()
        .map(FlashcardDTO::getQuestion)
        .filter(question -> question != null && !question.isBlank())
        .limit(6)
        .toList();

    PromptContext context = PromptContext.builder()
        .targetRole(profile.getTargetRole())
        .rawInput(profile.getExperienceLevel())
        .summaryMd(profile.getLearningPreference())
        .recommends(weakTopics)
        .keyPoints(reviewOrder)
        .ragMemories(flashcardPoints)
        .build();

    String prompt = promptBuilder.buildReviewPlanPrompt(context);

    try {
      String result = reviewPlannerAgent.plan(prompt);
      return jsonParseService.parseLearningPlan(result);
    } catch (Exception e) {
      return buildFallbackPlan(weakTopics, flashcards);
    }
  }

  private LearningPlanDTO buildFallbackPlan(
      List<String> weakTopics,
      List<FlashcardDTO> flashcards) {
    LearningPlanDTO plan = new LearningPlanDTO();
    plan.setTitle("3天弱项巩固计划");
    plan.setSummary("围绕当前最薄弱的知识点进行短周期复习、表达训练和闪卡复盘。");
    plan.setTotalDays(3);
    plan.setDailyMinutes(45);
    plan.setFocusAreas(weakTopics.isEmpty() ? List.of("基础知识", "表达训练") : weakTopics.stream().limit(3).toList());

    List<com.dto.dashboard.LearningPlanStepDTO> steps = new ArrayList<>();
    steps.add(step(
        1,
        firstOrDefault(weakTopics, "核心薄弱点"),
        "梳理核心概念、原理和常见面试问法，整理一版自己的回答结构。",
        List.of("能说明定义和原理", "能说出至少 2 个典型场景"),
        45));
    steps.add(step(
        2,
        weakTopics.size() > 1 ? weakTopics.get(1) : "场景应用",
        "结合项目或系统场景练习回答，补齐知识点之间的联系与取舍。",
        List.of("能结合项目举例", "能解释为什么这样设计"),
        45));
    steps.add(step(
        3,
        "输出复盘",
        "使用闪卡和口述方式完成一次模拟复盘，记录仍然不稳的点。",
        List.of(
            "完成一轮口述复盘",
            flashcards.isEmpty() ? "整理至少 2 个待强化点" : "完成至少 3 张闪卡复习"),
        45));
    plan.setSteps(steps);
    plan.setSuccessCriteria(List.of(
        "能独立讲清当前最弱知识点",
        "能完成一次较完整的模拟回答",
        "能明确下一轮需要继续强化的点"));
    return plan;
  }

  private com.dto.dashboard.LearningPlanStepDTO step(
      int dayIndex,
      String focus,
      String task,
      List<String> checkpoints,
      int estimatedMinutes) {
    com.dto.dashboard.LearningPlanStepDTO step = new com.dto.dashboard.LearningPlanStepDTO();
    step.setDayIndex(dayIndex);
    step.setFocus(focus);
    step.setTask(task);
    step.setCheckpoints(checkpoints);
    step.setEstimatedMinutes(estimatedMinutes);
    return step;
  }

  private String firstOrDefault(List<String> values, String fallback) {
    return values == null || values.isEmpty() ? fallback : values.get(0);
  }
}
