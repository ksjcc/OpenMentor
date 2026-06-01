package com.dto.dashboard;

import java.util.List;

import lombok.Data;

@Data
public class LearningPlanDTO {
  private String title;
  private String summary;
  private Integer totalDays;
  private Integer dailyMinutes;
  private List<String> focusAreas;
  private List<LearningPlanStepDTO> steps;
  private List<String> successCriteria;
}
