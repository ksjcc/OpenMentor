package com.ai.memory;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.dto.Retrieval.QueryType;
import com.dto.Retrieval.RetrievalPlanDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemoryLayerRouterService {
  private final MemoryStorageSelectorService memoryStorageSelectorService;

  public MemoryLayer resolve(String query, RetrievalPlanDTO plan) {
    return route(query, plan).getPrimaryLayer();
  }

  public MemoryLayerRouteDecision route(String query, RetrievalPlanDTO plan) {
    String normalized = normalize(query);
    QueryType queryType = plan == null ? null : plan.getQueryType();
    boolean contextDependent = plan != null && plan.isContextDependent();

    if (isSessionQuery(normalized, queryType, contextDependent)) {
      return buildDecision(
          MemoryLayer.SESSION,
          MemoryLayer.GROWTH,
          plan,
          "follow_up_or_current_session");
    }

    if (isPreferenceQuery(normalized, queryType)) {
      return buildDecision(
          MemoryLayer.PREFERENCE,
          MemoryLayer.GROWTH,
          plan,
          "user_preference_or_profile");
    }

    if (isGrowthQuery(normalized, queryType, plan)) {
      return buildDecision(
          MemoryLayer.GROWTH,
          MemoryLayer.PREFERENCE,
          plan,
          "long_term_growth_or_history");
    }

    if (contextDependent) {
      return buildDecision(
          MemoryLayer.SESSION,
          MemoryLayer.GROWTH,
          plan,
          "context_dependent_default");
    }

    return buildDecision(
        MemoryLayer.GROWTH,
        MemoryLayer.PREFERENCE,
        plan,
        "default_growth_memory");
  }

  private MemoryLayerRouteDecision buildDecision(
      MemoryLayer primary,
      MemoryLayer fallback,
      RetrievalPlanDTO plan,
      String reason) {
    return MemoryLayerRouteDecision.builder()
        .primaryLayer(primary)
        .fallbackLayer(fallback)
        .selectedStorage(memoryStorageSelectorService.select(primary, plan))
        .fallbackStorage(memoryStorageSelectorService.select(fallback, plan))
        .reason(reason)
        .build();
  }

  private boolean isSessionQuery(String query, QueryType queryType, boolean contextDependent) {
    return containsAny(query,
        "继续刚才",
        "刚才那个",
        "上一个问题",
        "上一题",
        "follow-up",
        "follow up",
        "继续追问",
        "当前面试",
        "这个问题继续")
        || queryType == QueryType.INTERVIEW_FOLLOWUP
        || (contextDependent && containsAny(query, "刚才", "上面", "之前这题"));
  }

  private boolean isGrowthQuery(String query, QueryType queryType, RetrievalPlanDTO plan) {
    return containsAny(query,
        "弱项",
        "薄弱",
        "之前不会",
        "历史表现",
        "学习记录",
        "长期成长",
        "成长轨迹",
        "复习",
        "我哪里不会")
        || queryType == QueryType.LEARNING
        || queryType == QueryType.MEMORY
        || (plan != null && plan.isNeedMemory() && !isPreferenceQuery(query, queryType));
  }

  private boolean isPreferenceQuery(String query, QueryType queryType) {
    return containsAny(query,
        "我的岗位",
        "我的方向",
        "targetrole",
        "学习偏好",
        "偏好",
        "配置",
        "目标岗位",
        "技术栈",
        "简历")
        || queryType == QueryType.MIXED && containsAny(query, "偏好", "岗位", "方向");
  }

  private boolean containsAny(String query, String... needles) {
    if (query == null || query.isBlank() || needles == null) {
      return false;
    }
    for (String needle : needles) {
      if (needle != null && !needle.isBlank() && query.contains(needle.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String query) {
    return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
  }
}
