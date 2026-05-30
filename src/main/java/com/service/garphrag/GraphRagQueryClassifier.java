package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.common.GraphRagMode;
import com.common.GraphRagRetrieverType;
import com.dto.graphrag.GraphRagRetrievalPlan;

@Component
public class GraphRagQueryClassifier {
  public GraphRagRetrievalPlan classify(Long userId, String query, boolean realTime) {
    return classify(query, realTime);
  }

  public GraphRagRetrievalPlan classify(String query, boolean realTime) {
    if (query == null || query.isBlank()) {
      return GraphRagRetrievalPlan.builder()
          .mode(GraphRagMode.LOCAL)
          .retrievers(List.of(GraphRagRetrieverType.LOCAL))
          .needRerank(false)
          .budget(2)
          .reason("空问题，默认使用低成本 LOCAL 检索。")
          .build();
    }

    String q = query.trim();
    int tokenLikeLength = normalizedLength(q);
    boolean global = isGlobalQuestion(q);
    boolean deepReasoning = isDeepReasoningQuestion(q);
    boolean localEntity = isLocalEntityQuestion(q);
    boolean crossTopic = isCrossTopicQuestion(q) || tokenLikeLength >= 36;
    boolean liveSignal = realTime || isRealtimeQuestion(q);

    if (global) {
      return GraphRagRetrievalPlan.builder()
          .mode(GraphRagMode.GLOBAL)
          .retrievers(List.of(
              GraphRagRetrieverType.GLOBAL,
              GraphRagRetrieverType.VECTOR,
              GraphRagRetrieverType.MEMORY))
          .needRerank(true)
          .budget(liveSignal ? 3 : 4)
          .reason("问题偏全局总结，优先社区报告，并补充向量与记忆信息。")
          .build();
    }

    if (deepReasoning && liveSignal) {
      return GraphRagRetrievalPlan.builder()
          .mode(GraphRagMode.LOCAL)
          .retrievers(List.of(
              GraphRagRetrieverType.LOCAL,
              GraphRagRetrieverType.VECTOR,
              GraphRagRetrieverType.MEMORY))
          .needRerank(true)
          .budget(3)
          .reason("问题需要推理，但当前是实时场景，先走低延迟的 LOCAL/VECTOR/MEMORY 组合。")
          .build();
    }

    if (deepReasoning || crossTopic) {
      return GraphRagRetrievalPlan.builder()
          .mode(GraphRagMode.DRIFT)
          .retrievers(List.of(
              GraphRagRetrieverType.DRIFT,
              GraphRagRetrieverType.GLOBAL,
              GraphRagRetrieverType.LOCAL,
              GraphRagRetrieverType.VECTOR))
          .needRerank(true)
          .budget(crossTopic ? 5 : 4)
          .reason("问题涉及跨主题或多跳推理，使用 DRIFT 作为主检索并做多源聚合。")
          .build();
    }

    if (localEntity) {
      return GraphRagRetrievalPlan.builder()
          .mode(GraphRagMode.LOCAL)
          .retrievers(List.of(
              GraphRagRetrieverType.LOCAL,
              GraphRagRetrieverType.VECTOR))
          .needRerank(tokenLikeLength >= 18)
          .budget(3)
          .reason("问题围绕明确知识点，使用 LOCAL 主检索，并用 VECTOR 补召回。")
          .build();
    }

    return GraphRagRetrievalPlan.builder()
        .mode(GraphRagMode.LOCAL)
        .retrievers(List.of(
            GraphRagRetrieverType.LOCAL,
            GraphRagRetrieverType.MEMORY))
        .needRerank(tokenLikeLength >= 20)
        .budget(3)
        .reason("未命中特殊规则，使用轻量 LOCAL + MEMORY 检索。")
        .build();
  }

  private int normalizedLength(String q) {
    return q.replaceAll("\\s+", "").length();
  }

  private boolean isRealtimeQuestion(String q) {
    return containsAny(q, "刚才", "上一题", "当前", "现在", "立刻", "马上", "这轮");
  }

  private boolean isCrossTopicQuestion(String q) {
    return containsAny(q,
        "结合", "对比", "区别", "权衡", "取舍", "链路", "体系",
        "从", "到", "同时", "分别", "多个", "跨");
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
