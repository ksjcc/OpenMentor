package com.service.garphrag;

import org.springframework.stereotype.Component;

import com.common.GraphRagMode;
import com.common.GraphRagQueryType;
import com.dto.graphrag.GraphRagQueryPlan;

@Component
public class GraphRagQueryClassifier {
  public GraphRagQueryPlan classify(String query, boolean realTime) {
    GraphRagQueryPlan plan = new GraphRagQueryPlan();
    if (query == null || query.isBlank()) {
      plan.setQueryType(GraphRagQueryType.SIMPLE_FACT);
      plan.setMode(GraphRagMode.LOCAL);
      plan.setReason("空问题，默认使用低成本 LOCAL 检索。");
      return plan;
    }

    String q = query.trim();
    if (isGlobalQuestion(q)) {
      plan.setQueryType(GraphRagQueryType.GLOBAL_SUMMARY);
      plan.setMode(GraphRagMode.GLOBAL);
      plan.setReason("问题关注整体能力、总结或全局薄弱点，使用 GLOBAL。");
      return plan;
    }

    if (isDeepReasoningQuestion(q)) {
      plan.setQueryType(GraphRagQueryType.DEEP_REASONING);

      if (realTime) {
        plan.setMode(GraphRagMode.LOCAL);
        plan.setReason("问题需要深度推理，但当前是实时面试场景，降级为 LOCAL。");
      } else {
        plan.setMode(GraphRagMode.DRIFT);
        plan.setReason("问题涉及原因链或多跳推理，使用 DRIFT。");
      }

      return plan;
    }

    if (isLocalEntityQuestion(q)) {
      plan.setQueryType(GraphRagQueryType.ENTITY_LOCAL);
      plan.setMode(GraphRagMode.LOCAL);
      plan.setReason("问题围绕明确知识点或当前追问主题，使用 LOCAL。");
      return plan;
    }

    plan.setQueryType(GraphRagQueryType.SIMPLE_FACT);
    plan.setMode(realTime ? GraphRagMode.LOCAL : GraphRagMode.AUTO);
    plan.setReason("未命中特殊规则，使用轻量检索。");

    return plan;
  }

  private boolean isGlobalQuestion(String q) {
    return containsAny(q,
        "整体", "总体", "全局", "总结", "汇总",
        "能力结构", "知识体系", "学习路径",
        "薄弱点排行", "最大短板", "主要问题",
        "成长建议", "复盘", "报告", "趋势");
  }

  private boolean isDeepReasoningQuestion(String q) {
    return containsAny(q,
        "为什么", "原因", "根因", "如何导致", "影响",
        "关联", "依赖", "推导", "怎么改进", "怎么提升");
  }

  private boolean isLocalEntityQuestion(String q) {
    return containsAny(q,
        "Redis", "JVM", "MySQL", "Spring", "事务", "索引", "缓存",
        "并发", "线程", "GC", "项目", "这个知识点", "这道题", "追问");
  }

  private boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
