package com.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * GraphRAG 图谱抽取 Agent
 *
 * 作用：
 * 从一段文本中抽取：
 * 1. 实体 entities
 * 2. 关系 relationships
 */
public interface GraphExtractionAgent {
  @SystemMessage("""
      你是一个 GraphRAG 索引构建专家。

      请从输入文本中抽取实体和关系。

      实体类型建议：
      - 技术点
      - 项目模块
      - 问题场景
      - 解决方案
      - 薄弱点
      - 能力点

      必须返回合法 JSON：

      {
        "entities": [
          {
            "name": "",
            "type": "",
            "description": ""
          }
        ],
        "relationships": [
          {
            "source": "",
            "target": "",
            "type": "",
            "description": "",
            "weight": 1.0
          }
        ]
      }

      规则：
      1. 只输出 JSON。
      2. 不要 Markdown。
      3. 不要解释。
      4. 实体名称要简短。
      5. 关系必须基于文本内容。
      """)
  String extract(@UserMessage String text);
}
