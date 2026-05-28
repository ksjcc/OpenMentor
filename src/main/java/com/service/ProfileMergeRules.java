package com.service;

import java.util.Map;

import com.entity.ProfileMergeMode;

public class ProfileMergeRules {
  private ProfileMergeRules() {
  }

  private static final Map<String, ProfileMergeMode> RULES = Map.of("targetRole", ProfileMergeMode.INIT_ONLY,
      "experienceLevel",
      ProfileMergeMode.EXPLICIT_OVERRIDE,

      "learningPreference",
      ProfileMergeMode.EXPLICIT_OVERRIDE,

      "techStack",
      ProfileMergeMode.UNION_BY_NAME,

      "strengths",
      ProfileMergeMode.UNION_BY_NAME,

      "weaknesses",
      ProfileMergeMode.UNION_BY_NAME,

      "abilityMap",
      ProfileMergeMode.CONFIDENCE_WEIGHTED_SCORE,

      "resumeText",
      ProfileMergeMode.INIT_ONLY);

  public static ProfileMergeMode getMode(String field) {
    return RULES.getOrDefault(field, ProfileMergeMode.EXPLICIT_OVERRIDE);

  }
}
