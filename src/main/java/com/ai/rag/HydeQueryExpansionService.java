package com.ai.rag;

import org.springframework.cache.annotation.Cacheable;
import com.ai.client.LlmClient;
import com.service.cache.AiQueryCacheService;

public class HydeQueryExpansionService {
  private LlmClient llmClient;

  private AiQueryCacheService cacheService;

  public String expand(String query) {
    if (query == null || query.isBlank()) {
      return query;
    }
    String prompt = """
          你是一个检索增强助手。
                  请根据用户问题，生成一段可能包含相关知识点的技术说明文本。
                  不要回答用户，不要编造结论。
                  目标是帮助向量检索覆盖更多相关技术概念。

                  用户问题：
                  %s
        """.formatted(query);
    String cached = cacheService.getHyde(query);

    if (cached != null) {
      return cached;
    }

    try {
      String hyde = llmClient.safeChat(
          prompt,
          () -> query);
      if (hyde == null || hyde.isBlank()) {
        return query;
      }
      cacheService.putHyde(query, hyde);
      return hyde.trim();
    } catch (Exception e) {
    }

    return query;

  }
}
