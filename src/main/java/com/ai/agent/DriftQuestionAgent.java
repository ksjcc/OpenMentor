package com.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DriftQuestionAgent {
  @SystemMessage("""
      你是 GraphRAG DRIFT 查询扩展专家。
      请根据用户问题和社区报告生成更具体的 local search 查询。
      只输出 JSON。
      """)
  String generateQuestions(@UserMessage String input);
}
