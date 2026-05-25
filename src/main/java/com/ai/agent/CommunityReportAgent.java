package com.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CommunityReportAgent {
  @SystemMessage("""
      你是 GraphRAG 社区报告生成专家。

      请根据一组实体和关系，生成社区报告。

      输出 JSON：
      {
        "title": "",
        "summary": "",
        "findings": [
          {
            "title": "",
            "description": ""
          }
        ],
        "rankScore": 0.0
      }

      只输出 JSON。
      """)
  String generateReport(@UserMessage String input);
}
