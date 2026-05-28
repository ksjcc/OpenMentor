package com.ai.agent.impl;

import com.ai.agent.GraphExtractionAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import org.springframework.stereotype.Component;

@Component
public class GraphExtractionAgentImpl extends AbstractSafeAgentSupport implements GraphExtractionAgent {

    private final PromptBuilder promptBuilder;

    public GraphExtractionAgentImpl(LlmClient llmClient, PromptBuilder promptBuilder) {
        super(llmClient);
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String extract(String text) {
        String prompt = promptBuilder.buildGraphExtractionPrompt(PromptContext.builder()
                .task(PromptTemplates.TASK_GRAPH_EXTRACTION)
                .rawInput(text)
                .build());

        return safeChat(
                prompt,
                () -> """
                        {
                          "entities": [],
                          "relationships": []
                        }
                        """);
    }
}
