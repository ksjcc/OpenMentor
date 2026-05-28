package com.ai.agent.impl;

import com.ai.agent.ResumeProfileAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import org.springframework.stereotype.Component;

@Component
public class ResumeProfileAgentImpl extends AbstractSafeAgentSupport implements ResumeProfileAgent {

    public ResumeProfileAgentImpl(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public String parseProfile(String resumeText) {
        return safeChat(
                resumeText,
                () -> """
                        {
                          "targetRole": "",
                          "experienceLevel": "",
                          "techStack": [],
                          "projects": [],
                          "strengths": [],
                          "weaknesses": [],
                          "abilityMap": {},
                          "learningPreference": "fallback"
                        }
                        """);
    }
}
