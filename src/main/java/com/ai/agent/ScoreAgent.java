package com.ai.agent;

import dev.langchain4j.service.UserMessage;

public interface ScoreAgent {

  String score(@UserMessage String input);
}
