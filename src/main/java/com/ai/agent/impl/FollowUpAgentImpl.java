package com.ai.agent.impl;

import com.ai.Stream.StreamingChunkHandler;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;
import org.springframework.stereotype.Component;

@Component
public class FollowUpAgentImpl extends AbstractSafeAgentSupport implements FollowUpAgent {

    public FollowUpAgentImpl(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public String streamFollowUp(String requestId, String prompt, StreamingChunkHandler handler) {
        return safeStreamChat(
                requestId,
                prompt,
                handler,
                () -> "你刚才的回答还不够完整。请补充说明核心原理、关键流程，以及在真实项目中你会如何处理这个问题。");
    }
}
