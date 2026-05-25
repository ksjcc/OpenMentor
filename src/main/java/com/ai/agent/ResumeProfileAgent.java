package com.ai.agent;

import dev.langchain4j.service.UserMessage;

/**
 * 简历画像解析 Agent
 *
 * 作用：interface
 * 将用户输入的简历、项目经历、自我介绍文本解析成结构化职业画像 JSON。
 */
public interface ResumeProfileAgent {
  String parseProfile(@UserMessage String resumeText);
}
