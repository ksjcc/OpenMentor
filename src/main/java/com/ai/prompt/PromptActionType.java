package com.ai.prompt;

public enum PromptActionType {
  NEW_QUESTION,

  FOLLOW_UP,

  NEXT,

  HARDER,

  SCORE,

  DECISION,

  EXTRACTION,

  PROFILE,

  GRAPH_EXTRACTION,

  COMMUNITY_REPORT,

  DRIFT_QUERY;

  public static PromptActionType fromTask(String task) {
    if (task == null || task.isBlank()) {
      return NEW_QUESTION;
    }
    return switch (task) {
      case PromptTemplates.TASK_NEW_QUESTION -> NEW_QUESTION;
      case PromptTemplates.TASK_HARDER -> HARDER;
      case PromptTemplates.TASK_FOLLOW_UP -> FOLLOW_UP;
      case PromptTemplates.TASK_SCORE -> SCORE;
      case PromptTemplates.TASK_DECISION -> DECISION;
      case PromptTemplates.TASK_EXTRACTION -> EXTRACTION;
      case PromptTemplates.TASK_PROFILE -> PROFILE;
      case PromptTemplates.TASK_GRAPH_EXTRACTION -> GRAPH_EXTRACTION;
      case PromptTemplates.TASK_COMMUNITY_REPORT -> COMMUNITY_REPORT;
      case PromptTemplates.TASK_DRIFT_QUERY -> DRIFT_QUERY;
      default -> NEW_QUESTION;
    };
  }
}
