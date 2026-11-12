package com.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.client.LlmClient;
import com.ai.rag.HybridRetriever;

import lombok.RequiredArgsConstructor;

/**
 * 面向知识学习场景的问答服务。
 *
 * <p>改造后不再依赖已删除的检索规划器，直接用 HybridRetriever 的
 * (userId, query, topK) 入口。检索规划这一步在 harness 里由模型通过工具完成，
 * 不再有独立的规划组件。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeQaService {
  private final HybridRetriever hybridRetriever;
  private final LlmClient llmClient;

  public String answer(Long userId, String question) {
    List<String> contexts = new ArrayList<>(hybridRetriever.retrieve(userId, question, 8));

    List<String> mergedContexts = contexts.stream()
        .filter(text -> text != null && !text.isBlank())
        .distinct()
        .limit(8)
        .toList();

    String prompt = buildAnswerPrompt(question, mergedContexts);
    return llmClient.safeChat(
        prompt,
        () -> "我暂时没能组织出稳定答案，请稍后重试。");
  }

  private String buildAnswerPrompt(String question, List<String> contexts) {
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

        用户问题：
        %s

        检索上下文：
        %s
        """.formatted(
        question == null ? "" : question,
        contextBlock);
  }
}
