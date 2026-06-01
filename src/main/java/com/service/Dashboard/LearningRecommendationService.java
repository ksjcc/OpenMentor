package com.service.Dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ai.client.LlmClient;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.dashboard.LearningRecommendationDTO;
import com.dto.extraction.FlashcardDTO;
import com.entity.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.UserProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningRecommendationService {
  private final UserProfileMapper userProfileMapper;
  private final FlashcardService flashcardService;
  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;
  private final PromptBuilder promptBuilder;

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
    return LearningRecommendationDTO.builder()
        .weakTopics(weakTopics)
        .reviewOrder(reviewOrder)
        .flashcards(flashcards)
        .roleGapSuggestions(roleGapSuggestions)
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
}
