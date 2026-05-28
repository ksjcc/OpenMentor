package com.ai.agent.impl;

import com.ai.agent.ScoreAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import org.springframework.stereotype.Component;

@Component
public class ScoreAgentImpl extends AbstractSafeAgentSupport implements ScoreAgent {

    public ScoreAgentImpl(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public String score(String input) {
        return safeChat(
                input,
                () -> """
                        {
                          "score": 60,
                          "technical": 20,
                          "completeness": 20,
                          "expression": 20,
                          "isWeak": true,
                          "feedback": "fallback",
                          "weakPoints": []
                        }
                        """);
    }
}
