package com.ai.agent.impl;

import com.ai.agent.DriftQuestionAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import org.springframework.stereotype.Component;

@Component
public class DriftQuestionAgentImpl extends AbstractSafeAgentSupport implements DriftQuestionAgent {

    private final PromptBuilder promptBuilder;

    public DriftQuestionAgentImpl(LlmClient llmClient, PromptBuilder promptBuilder) {
        super(llmClient);
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String generateQuestions(String input) {
        String prompt = promptBuilder.buildDriftQuestionPrompt(PromptContext.builder()
                .task(PromptTemplates.TASK_DRIFT_QUERY)
                .rawInput(input)
                .build());

        return safeChat(
                prompt,
                () -> """
                        {
                          "queries": []
                        }
                        """);
    }
}
