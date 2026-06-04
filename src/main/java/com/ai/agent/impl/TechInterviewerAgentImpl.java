package com.ai.agent.impl;

import com.ai.Stream.StreamingChunkHandler;
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
    public String streamQuestion(String requestId, String input, StreamingChunkHandler handler) {
        return safeStreamChat(
                requestId,
                input,
                handler,
                () -> "请你先介绍一下这个问题的核心概念，并结合项目经验展开说明。");
    }
}
