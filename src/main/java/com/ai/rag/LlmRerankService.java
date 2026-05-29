package com.ai.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.client.LlmClient;
import com.service.garphrag.RetrievalBudget;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlmRerankService implements RerankService {
  private final LlmClient llmClient;

  @Override
  public List<String> rerank(String query,
      List<String> candidates,
      int topK) {
    return rerank(query, candidates, topK, null);
  }

  @Override
  public List<String> rerank(String query,
      List<String> candidates,
      int topK,
      RetrievalBudget budget) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    if (candidates.size() <= topK) {
      return candidates;
    }
    if (budget != null && !budget.consumeLlmCall()) {
      return candidates.stream()
          .limit(topK)
          .toList();
    }
    StringBuilder prompt = new StringBuilder();

    prompt.append("""
        你是RAG重排器。

        用户问题：
        """)
        .append(query)
        .append("\n\n");

    prompt.append("""
        请根据与问题的相关性，
        从下面候选内容中选出最相关的内容，
        按相关度从高到低返回编号，
        只返回编号数组。

        例如：
        [3,1,2]

        候选：
        """);
    for (int i = 0; i < candidates.size(); i++) {
      prompt.append("\n[")
          .append(i)
          .append("]\n")
          .append(candidates.get(i))
          .append("\n");
    }
    String result = llmClient.chat(prompt.toString());
    return parseIndexes(
        result,
        candidates,
        topK);
  }

  private List<String> parseIndexes(
      String result,
      List<String> candidates,
      int topK) {

    List<String> reranked = new ArrayList<>();

    String cleaned = result.replaceAll("[\\[\\]\\s]", "");
    String[] indexes = cleaned.split(",");

    for (String index : indexes) {

      try {
        int i = Integer.parseInt(index);

        if (i >= 0
            && i < candidates.size()) {

          reranked.add(
              candidates.get(i));
        }

        if (reranked.size() >= topK) {
          break;
        }

      } catch (Exception ignored) {
      }
    }

    if (reranked.isEmpty()) {
      return candidates.stream()
          .limit(topK)
          .toList();
    }

    return reranked;
  }

}
