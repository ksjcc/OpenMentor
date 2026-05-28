package com.ai.prompt;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PromptBuilder
 *
 * AI Prompt 构建器，统一拼接：
 * 1. 用户画像
 * 2. 长期记忆
 * 3. RAG 召回记忆
 * 4. Redis 短期上下文
 * 5. 当前任务
 * 6. 输出规则
 */
@Component
public class PromptBuilder {

  public String buildQuestionPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个专业的 Java 后端技术面试官。

        你的职责：
        1. 提出真实企业面试中可能出现的问题
        2. 优先考察用户薄弱点
        3. 结合用户项目经历和历史表现
        4. 每次只问一个问题

        %s

        ====================================
        【当前面试主题】
        ====================================

        %s

        ====================================
        【任务】
        ====================================

        请生成一个新的技术面试问题。

        要求：
        1. 问题贴近真实企业
        2. 不要过于八股
        3. 优先围绕用户薄弱点
        4. 可以结合项目场景
        5. 只输出问题
        6. 不要解释
        7. 不要输出答案
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getTopic()));
  }

  public String buildHarderQuestionPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
            你是一个严格的 Java 后端高级面试官。

            【用户画像】
            %s

            【长期记忆】
            %s

            【RAG召回】
            %s

            【最近上下文】
            %s

            【当前主题】
            %s

            【任务】
            用户刚才回答质量较高，请提出一个更高难度的问题。

            要求：
            1. 问题必须更深入
            2. 可以涉及原理、源码、系统设计或项目落地
            3. 只输出一个问题
            4. 不要给答案
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getProfileJson()),
        safe(promptContext.getSummaryMd()),
        joinList(promptContext.getRagMemories()),
        safe(promptContext.getRecentContext()),
        safe(promptContext.getTopic()));
  }

  public String buildFollowUpPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个专业 Java 后端技术面试官。

        你的特点：
        1. 严格
        2. 擅长深挖
        3. 围绕用户薄弱点追问
        4. 问题贴近真实企业面试

        %s

        ====================================
        【原问题】
        ====================================

        %s

        ====================================
        【用户回答】
        ====================================

        %s

        ====================================
        【评分结果】
        ====================================

        %s

        ====================================
        【任务】
        ====================================

        ====================================
        【Knowledge Graph 推理】
        ====================================

        %s

        用户刚才回答得不完整，请围绕同一个问题继续追问。

        要求：
        1. 只输出一个追问问题
        2. 不要解释
        3. 不要输出答案
        4. 不要重复原问题
        5. 优先围绕薄弱点追问
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getCurrentQuestion()),
        safe(promptContext.getCurrentAnswer()),
        safe(promptContext.getScoreJson()),
        safe(context.getGraphReasoning()));
  }

  public String buildScorePrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个严格的技术面试评分官。

        %s

        ====================================
        【当前面试主题】
        ====================================

        %s

        ====================================
        【问题】
        ====================================

        %s

        ====================================
        【用户回答】
        ====================================

        %s

        ====================================
        【评分规则】
        ====================================

        请根据用户回答，从以下维度评分：
        1. 技术正确性（0-40）
        2. 完整性（0-30）
        3. 表达清晰度（0-30）

        总分 100。

        ====================================
        【输出要求】
        ====================================

        必须输出 JSON：

        {
          "score": 0,
          "technical": 0,
          "completeness": 0,
          "expression": 0,
          "isWeak": true,
          "feedback": "",
          "weakPoints": []
        }

        不要输出 Markdown。
        不要解释。
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getTopic()),
        safe(promptContext.getCurrentQuestion()),
        safe(promptContext.getCurrentAnswer()));
  }

  public String buildDecisionPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个面试策略决策专家。

        %s

        ====================================
        【当前面试主题】
        ====================================

        %s

        ====================================
        【问题】
        ====================================

        %s

        ====================================
        【用户回答】
        ====================================

        %s

        ====================================
        【评分结果】
        ====================================

        %s

        ====================================
        【决策规则】
        ====================================

        1. score < 60：继续追问当前问题
        2. 60 <= score <= 80：进入下一题或同主题换角度
        3. score > 80：提高难度或进入下一题
        4. 如果用户明显答非所问，优先 FOLLOW_UP
        5. 如果最近上下文已经连续追问同一知识点，优先 NEXT

        ====================================
        【输出要求】
        ====================================

        必须输出 JSON：

        {
          "action": "FOLLOW_UP",
          "reason": ""
        }

        action 只能是 FOLLOW_UP、NEXT、HARDER 之一。
        不要输出 Markdown。
        不要解释。
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getTopic()),
        safe(promptContext.getCurrentQuestion()),
        safe(promptContext.getCurrentAnswer()),
        safe(promptContext.getScoreJson()));
  }

  public String buildKnowledgeExtractionPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个面试知识提炼专家。

        ====================================
        【本场面试完整对话】
        ====================================

        %s

        ====================================
        【任务】
        ====================================

        请从面试对话中提取：
        1. 用户掌握的知识点
        2. 用户薄弱的知识点
        3. 可以生成闪卡的问题和答案
        4. 适合写入长期记忆的总结
        5. 下一次面试建议

        ====================================
        【输出要求】
        ====================================

        必须输出 JSON，格式如下：

        {
          "summary": "本次面试总结",
          "strengths": [
            {
              "name": "知识点名称",
              "evidence": "依据"
            }
          ],
          "weaknesses": [
            {
              "name": "薄弱点名称",
              "level": 1,
              "evidence": "依据"
            }
          ],
          "flashcards": [
            {
              "question": "问题",
              "answer": "答案",
              "difficulty": 1
            }
          ],
          "memory": {
            "profileUpdate": "适合写入JSON长期记忆的内容",
            "markdownSummary": "适合写入Markdown长期总结的内容",
            "chunks": [
              "适合RAG召回的记忆片段1",
              "适合RAG召回的记忆片段2"
            ]
          },
          "nextPlan": [
            "下一步训练建议1",
            "下一步训练建议2"
          ]
        }

        只输出 JSON。
        不要输出解释。
        不要使用 Markdown 包裹 JSON。
        """.formatted(safe(promptContext.getDialogue()));
  }

  public String buildProfilePrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个专业的程序员求职画像分析师。

        ====================================
        【用户目标岗位】
        ====================================

        %s

        ====================================
        【用户简历 / 项目经历】
        ====================================

        %s

        ====================================
        【任务】
        ====================================

        请根据用户输入的简历、项目经历和目标岗位，提取结构化职业画像。

        ====================================
        【输出要求】
        ====================================

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

        只输出 JSON。
        不要用 Markdown 包裹。
        不要解释。
        """.formatted(
        safe(promptContext.getTargetRole()),
        safe(promptContext.getResumeText()));
  }

  public String buildGraphExtractionPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
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

        输入文本：
        %s
        """.formatted(safe(promptContext.getRawInput()));
  }

  public String buildCommunityReportPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
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

        输入内容：
        %s
        """.formatted(safe(promptContext.getRawInput()));
  }

  public String buildDriftQuestionPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        你是 GraphRAG DRIFT 查询扩展专家。
        请根据用户问题和社区报告生成更具体的 local search 查询。
        只输出 JSON。

        输入内容：
        %s
        """.formatted(safe(promptContext.getRawInput()));
  }

  public String buildByTask(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    if (promptContext.getTask() == null) {
      return buildQuestionPrompt(promptContext);
    }

    return switch (promptContext.getTask()) {
      case PromptTemplates.TASK_NEW_QUESTION -> buildQuestionPrompt(promptContext);
      case PromptTemplates.TASK_HARDER -> buildHarderQuestionPrompt(promptContext);
      case PromptTemplates.TASK_FOLLOW_UP -> buildFollowUpPrompt(promptContext);
      case PromptTemplates.TASK_SCORE -> buildScorePrompt(promptContext);
      case PromptTemplates.TASK_DECISION -> buildDecisionPrompt(promptContext);
      case PromptTemplates.TASK_EXTRACTION -> buildKnowledgeExtractionPrompt(promptContext);
      case PromptTemplates.TASK_PROFILE -> buildProfilePrompt(promptContext);
      case PromptTemplates.TASK_GRAPH_EXTRACTION -> buildGraphExtractionPrompt(promptContext);
      case PromptTemplates.TASK_COMMUNITY_REPORT -> buildCommunityReportPrompt(promptContext);
      case PromptTemplates.TASK_DRIFT_QUERY -> buildDriftQuestionPrompt(promptContext);
      default -> buildQuestionPrompt(promptContext);
    };
  }

  /**
   * 兼容旧调用；内部仍走 PromptContext 统一编排。
   */
  public String buildTechInterviewInputwithMemoryTexts(
      String profileJson,
      String summaryMd,
      List<String> memoryTexts,
      String topic) {
    return buildQuestionPrompt(PromptContext.builder()
        .profileJson(profileJson)
        .summaryMd(summaryMd)
        .ragMemories(memoryTexts)
        .topic(topic)
        .task(PromptTemplates.TASK_NEW_QUESTION)
        .build());
  }

  private String buildMemoryBlock(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        ====================================
        【用户长期画像】
        ====================================

        %s

        ====================================
        【长期记忆】
        ====================================

        %s

        ====================================
        【RAG 召回的历史记忆】
        ====================================

        %s

        ====================================
        【最近面试上下文（Redis短期记忆）】
        ====================================

        %s
        """.formatted(
        safe(promptContext.getProfileJson()),
        safe(promptContext.getSummaryMd()),
        joinList(promptContext.getRagMemories()),
        safe(promptContext.getRecentContext()));
  }

  private String joinList(List<String> list) {
    if (list == null || list.isEmpty()) {
      return "暂无";
    }

    StringBuilder result = new StringBuilder();
    for (String item : list) {
      if (item == null || item.isBlank()) {
        continue;
      }
      result.append("- ").append(item.trim()).append("\n");
    }

    if (result.isEmpty()) {
      return "暂无";
    }
    return result.toString();
  }

  private String safe(String value) {
    return value == null || value.isBlank()
        ? "暂无"
        : value;
  }

  private PromptContext emptyIfNull(PromptContext context) {
    return context == null ? PromptContext.builder().build() : context;
  }
}
