package com.dto.dashboard;

import java.util.List;

import lombok.Data;

@Data
public class LearningPlanStepDTO {
  private Integer dayIndex;
  private String focus;
  private String task;
  private List<String> checkpoints;
  private Integer estimatedMinutes;
}
