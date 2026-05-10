package com.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 简历画像解析 Agent
 *
 * 作用：interface
 * 将用户输入的简历、项目经历、自我介绍文本解析成结构化职业画像 JSON。
 */
public interface ResumeProfileAgent {
  @SystemMessage("""
      你是一个专业的程序员求职画像分析师。

      请根据用户输入的简历/项目经历，提取结构化职业画像。

      必须返回 JSON，格式如下：
      {
        "targetRole": "目标岗位",
        "experienceLevel": "校招/实习/初级/中级",
        "techStack": ["技术1", "技术2"],
        "projects": [
          {
            "name": "项目名称",
            "description": "项目描述",
            "techStack": ["技术1", "技术2"],
            "highlights": ["亮点1", "亮点2"],
            "possibleQuestions": ["可能被追问的问题1", "可能被追问的问题2"]
          }
        ],
        "strengths": [
          {
            "name": "优势",
            "evidence": "依据"
          }
        ],
        "weaknesses": [
          {
            "name": "潜在薄弱点",
            "level": 1,
            "evidence": "依据"
          }
        ],
        "abilityMap": {
          "Java": 60,
          "Spring Boot": 60,
          "MySQL": 60,
          "Redis": 50,
          "Project Expression": 50
        },
        "learningPreference": "学习偏好"
      }

      注意：
      - 只输出 JSON
      - 不要用 Markdown 包裹
      - 不要解释
      """)
  String parseProfile(@UserMessage String resumeText);
}
