package com.ai.agent.impl;

import com.ai.agent.TechInterviewerAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import org.springframework.stereotype.Component;

@Component
public class TechInterviewerAgentImpl extends AbstractSafeAgentSupport implements TechInterviewerAgent {

    public TechInterviewerAgentImpl(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public String askQuestion(String input) {
        return safeChat(
                input,
                () -> "请你结合一个实际项目场景，说明 Redis 缓存击穿的成因，以及你会如何设计兜底方案？");
    }
}
