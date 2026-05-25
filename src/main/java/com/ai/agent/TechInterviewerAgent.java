package com.ai.agent;

import dev.langchain4j.service.UserMessage;


public interface TechInterviewerAgent {

    String askQuestion(@UserMessage String input);
}
