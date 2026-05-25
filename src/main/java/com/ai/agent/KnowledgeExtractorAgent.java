package com.ai.agent;

import dev.langchain4j.service.UserMessage;

public interface KnowledgeExtractorAgent {
  String extract(@UserMessage String interviewContent);
}
