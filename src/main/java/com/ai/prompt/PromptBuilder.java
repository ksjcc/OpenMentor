package com.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildSystemPrompt(String template, String role) {
        return template.replace("{role}", role);
    }
}
