package com.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.client.LlmClient;
import com.ai.decision.RetrievalPlanningService;
import com.ai.rag.HybridRetriever;
import com.common.GraphRagMode;
import com.dto.graphrag.RetrievalPlanDTO;
import com.service.garphrag.GraphRagRetriever;
import com.service.garphrag.RetrievalBudget;
import com.service.garphrag.RetrievalBudgetService;

import lombok.RequiredArgsConstructor;

/**
 * 面向知识学习场景的问答服务。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeQaService {
  private final RetrievalPlanningService retrievalPlanningService;
  private final RetrievalBudgetService retrievalBudgetService;
  private final HybridRetriever hybridRetriever;
  private final GraphRagRetriever graphRagRetriever;
  private final LlmClient llmClient;

  public String answer(Long userId, String question) {
    RetrievalPlanDTO plan = retrievalPlanningService.plan(question, "", question);
    RetrievalBudget budget = retrievalBudgetService.createBudget();

    List<String> contexts = new ArrayList<>();
    if (plan.isNeedMemory()) {
      contexts.addAll(hybridRetriever.retrieve(userId, plan.getQuery(), safeTopK(plan.getTopK())));
    }
    if (plan.isNeedGraph()) {
      GraphRagMode mode = plan.getGraphMode() == null ? GraphRagMode.AUTO : plan.getGraphMode();
      contexts.addAll(graphRagRetriever.retrieve(userId, plan.getQuery(), mode, false, safeTopK(plan.getTopK()), budget));
    }

    List<String> mergedContexts = contexts.stream()
        .filter(text -> text != null && !text.isBlank())
        .distinct()
        .limit(8)
        .toList();

    String prompt = buildAnswerPrompt(question, mergedContexts, plan);
    return llmClient.safeChat(
        prompt,
        () -> "我暂时没能组织出稳定答案，请稍后重试。");
  }

  private int safeTopK(Integer topK) {
    return topK == null || topK <= 0 ? 5 : topK;
  }

  private String buildAnswerPrompt(
      String question,
      List<String> contexts,
      RetrievalPlanDTO plan) {
    String contextBlock = contexts.isEmpty()
        ? "暂无可用检索上下文，请基于通用技术知识谨慎回答，并明确指出未命中用户专属上下文。"
        : String.join("\n\n---\n\n", contexts);

    return """
        你是 AI 技术成长 Copilot 的知识问答助手。
        你的目标不是进行面试，而是帮助开发者学习、理解和澄清技术知识点。

        回答要求：
        1. 优先基于给定上下文作答，不要假装看到了不存在的资料。
        2. 先给结论，再给关键原因或步骤。
        3. 如果上下文不足，要明确说明，并给出稳妥的通用解释。
        4. 回答风格偏技术辅导，不要使用“下一题”“追问”等面试语气。

        检索计划：
        %s

        用户问题：
        %s

        检索上下文：
        %s
        """.formatted(
        plan.getReason() == null ? "unknown" : plan.getReason(),
        question == null ? "" : question,
        contextBlock);
  }
}
