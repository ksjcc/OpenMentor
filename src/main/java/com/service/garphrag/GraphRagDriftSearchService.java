package com.service.garphrag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.agent.DriftQuestionAgent;
import com.service.JsonParseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraphRagDriftSearchService {

  private final GraphRagGlobalSearchService globalSearchService;
  private final GraphRagLocalSearchService localSearchService;
  private final DriftQuestionAgent driftQuestionAgent;
  private final JsonParseService jsonParseService;

  /**
   * DRIFT-lite：
   * 1. 先查 community reports
   * 2. 让 LLM 生成细化查询
   * 3. 对细化查询执行 Local Search
   */
  public List<String> search(Long userId, String query) {
    List<String> globalContext = globalSearchService.search(userId, query);
    String prompt = """
         用户问题：
        %s

        社区报告：
        %s

        请生成 3 个用于进一步 local search 的细化查询。
        返回 JSON：
        {
          "queries": []
        }
        """.formatted(query, String.join("\n", globalContext));
    String json = driftQuestionAgent.generateQuestions(prompt);
    List<String> expandedQueries = jsonParseService.parseDriftQuestion(json);
    List<String> result = new ArrayList<>();
    result.addAll(globalContext);
    for (String q : expandedQueries) {
      result.addAll(localSearchService.search(userId, q));
    }
    return result.stream()
        .distinct()
        .limit(15)
        .toList();
  }

}
