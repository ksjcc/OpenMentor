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
        【目标岗位】
        ====================================

        %s

        ====================================
        【简历 / 项目经历摘要】
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
        5. 尽量贴合目标岗位的真实考察重点
        6. 如果简历中有项目经历，优先从项目追问切入
        7. 只输出问题
        8. 不要解释
        9. 不要输出答案
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getTopic()),
        safe(promptContext.getTargetRole()),
        candidateBackground(promptContext));
  }

  public List<String> buildRecommendsPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return List.of(
        "【角色】",
        "你是一个专业的程序员求职成长顾问。",
        "",
        "你的职责：",
        "1. 结合目标岗位给出学习建议",
        "2. 优先围绕用户当前最薄弱的能力点",
        "3. 建议要具体、可执行、适合近期复习",
        "4. 每条建议尽量简洁，不空泛",
        "",
        "====================================",
        "【目标岗位】",
        "====================================",
        safe(promptContext.getTargetRole()),
        "",
        "====================================",
        "【当前薄弱点】",
        "====================================",
        joinInlineList(promptContext.getRecommends()),
        "",
        "====================================",
        "【任务】",
        "====================================",
        "请针对这些薄弱点，输出 3 条岗位补短板建议。",
        "",
        "要求：",
        "1. 每条建议单独一行",
        "2. 建议必须和目标岗位匹配",
        "3. 优先给出具体复习方向、实践方式或项目训练建议",
        "4. 不要输出序号",
        "5. 不要解释",
        "6. 不要输出多余开场白");
  }

  public String buildReviewPlanPrompt(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    return """
        【角色】
        你是一个专业的技术学习规划师。

        你的职责：
        1. 围绕用户当前最核心的薄弱点制定短周期学习计划
        2. 让计划与目标岗位要求保持一致
        3. 每天都给出明确、可执行、可检查的训练任务
        4. 结合已有闪卡与知识点，避免空泛建议

        ====================================
        【目标岗位】
        ====================================

        %s

        ====================================
        【经验阶段】
        ====================================

        %s

        ====================================
        【学习偏好】
        ====================================

        %s

        ====================================
        【当前薄弱点】
        ====================================

        %s

        ====================================
        【推荐复习顺序】
        ====================================

        %s

        ====================================
        【可用知识卡片 / 重点】
        ====================================

        %s

        ====================================
        【任务】
        ====================================

        请输出一个 3 到 5 天的结构化学习计划。

        输出要求：
        1. 必须输出合法 JSON
        2. 不要输出 Markdown
        3. 不要输出解释性文字
        4. 每一天都要有 focus、task、checkpoints、estimatedMinutes
        5. task 要足够具体，能直接执行
        6. checkpoints 要能用于自查

        JSON 格式如下：
        {
          "title": "",
          "summary": "",
          "totalDays": 0,
          "dailyMinutes": 0,
          "focusAreas": ["", ""],
          "steps": [
            {
              "dayIndex": 1,
              "focus": "",
              "task": "",
              "checkpoints": ["", ""],
              "estimatedMinutes": 0
            }
          ],
          "successCriteria": ["", ""]
        }
        """.formatted(
        safe(promptContext.getTargetRole()),
        safe(promptContext.getRawInput()),
        safe(promptContext.getSummaryMd()),
        joinInlineList(promptContext.getRecommends()),
        joinInlineList(promptContext.getKeyPoints()),
        joinList(promptContext.getRagMemories()));
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

            【目标岗位】
            %s

            【简历 / 项目经历摘要】
            %s

            【任务】
            用户刚才回答质量较高，请提出一个更高难度的问题。

            要求：
            1. 问题必须更深入
            2. 可以涉及原理、源码、系统设计或项目落地
            3. 尽量贴合目标岗位对该主题的更高要求
            4. 可以结合用户项目经历继续深挖
            5. 只输出一个问题
            6. 不要给答案
        """.formatted(
        safe(promptContext.getProfileJson()),
        safe(promptContext.getSummaryMd()),
        joinList(promptContext.getRagMemories()),
        safe(promptContext.getRecentContext()),
        safe(promptContext.getTopic()),
        safe(promptContext.getTargetRole()),
        candidateBackground(promptContext));
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
        【目标岗位】
        ====================================

        %s

        ====================================
        【简历 / 项目经历摘要】
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
        6. 可以结合目标岗位和项目背景继续深挖
        """.formatted(
        buildMemoryBlock(promptContext),
        safe(promptContext.getCurrentQuestion()),
        safe(promptContext.getCurrentAnswer()),
        safe(promptContext.getScoreJson()),
        safe(promptContext.getTargetRole()),
        candidateBackground(promptContext),
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
      case PromptTemplates.TASK_REVIEW_PLAN -> buildReviewPlanPrompt(promptContext);
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

  private String joinInlineList(List<String> list) {
    if (list == null || list.isEmpty()) {
      return "暂无";
    }

    return list.stream()
        .filter(item -> item != null && !item.isBlank())
        .map(String::trim)
        .reduce((left, right) -> left + "、" + right)
        .orElse("暂无");
  }

  private String safe(String value) {
    return value == null || value.isBlank()
        ? "暂无"
        : value;
  }

  private String candidateBackground(PromptContext context) {
    PromptContext promptContext = emptyIfNull(context);
    if (promptContext.getProjectSummary() != null && !promptContext.getProjectSummary().isBlank()) {
      return promptContext.getProjectSummary();
    }
    return safe(promptContext.getResumeText());
  }

  private PromptContext emptyIfNull(PromptContext context) {
    return context == null ? PromptContext.builder().build() : context;
  }
}
