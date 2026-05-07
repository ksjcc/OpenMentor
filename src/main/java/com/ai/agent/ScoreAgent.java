package com.ai.agent;

import org.springframework.stereotype.Component;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ScoreAgent {

  @SystemMessage("""
      你是一个严格的技术面试评分官。

      请根据用户回答，从以下维度评分：
      1. 技术正确性（0-40）
      2. 完整性（0-30）
      3. 表达清晰度（0-30）

      总分100。

      请输出JSON格式：
      {
        "score": 总分,
        "technical": 技术分,
        "completeness": 完整性,
        "expression": 表达,
        "isWeak": true/false,
        "feedback": "评价",
        "weakPoints": ["薄弱点1","薄弱点2"]
      }

      注意：
      - 必须返回JSON
      - 不要解释
      """)

  String score(@UserMessage String input);
}
