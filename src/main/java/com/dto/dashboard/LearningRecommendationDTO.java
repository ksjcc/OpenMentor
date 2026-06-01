package com.dto.dashboard;

import java.util.List;

import com.dto.extraction.FlashcardDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LearningRecommendationDTO {
  // 本周弱项主题
  private List<String> weakTopics;
  // 推荐复习顺序
  private List<String> reviewOrder;
  // 对应知识闪卡
  private List<FlashcardDTO> flashcards;
  // 岗位补短板建议
  private List<String> roleGapSuggestions;
  // 结构化学习计划
  private LearningPlanDTO learningPlan;
}
