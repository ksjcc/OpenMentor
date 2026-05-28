package com.ai.agent.impl;

import com.ai.agent.CommunityReportAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import org.springframework.stereotype.Component;

@Component
public class CommunityReportAgentImpl extends AbstractSafeAgentSupport implements CommunityReportAgent {

    private final PromptBuilder promptBuilder;

    public CommunityReportAgentImpl(LlmClient llmClient, PromptBuilder promptBuilder) {
        super(llmClient);
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String generateReport(String input) {
        String prompt = promptBuilder.buildCommunityReportPrompt(PromptContext.builder()
                .task(PromptTemplates.TASK_COMMUNITY_REPORT)
                .rawInput(input)
                .build());

        return safeChat(
                prompt,
                () -> """
                        {
                          "title": "fallback",
                          "summary": "fallback",
                          "findings": [],
                          "rankScore": 0.0
                        }
                        """);
    }
}
