package com.ai.agent;

import dev.langchain4j.service.UserMessage;

public interface FollowUpAgent {

    String askFollowUP(@UserMessage String prompt);

    default String askFollowUp(String prompt) {
        return askFollowUP(prompt);
    }

    default String decide(String prompt) {
        return askFollowUP(prompt);
    }
}
