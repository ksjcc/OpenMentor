package com.ai.agent.impl;

import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeExtractorAgentImpl extends AbstractSafeAgentSupport implements KnowledgeExtractorAgent {

    public KnowledgeExtractorAgentImpl(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public String extract(String interviewContent) {
        return safeChat(
                interviewContent,
                () -> """
                        {
                          "summary": "fallback",
                          "strengths": [],
                          "weaknesses": [],
                          "flashcards": [],
                          "memory": {
                            "profileUpdate": "",
                            "markdownSummary": "fallback",
                            "chunks": []
                          },
                          "nextPlan": []
                        }
                        """);
    }
}
