package com.ai.agent.support;

import com.ai.Stream.StreamingChunkHandler;
import com.ai.client.LlmClient;
import java.util.function.Supplier;

public abstract class AbstractSafeAgentSupport {

    private final LlmClient llmClient;

    protected AbstractSafeAgentSupport(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    protected String safeChat(String prompt, Supplier<String> fallbackSupplier) {
        return llmClient.safeChat(prompt, fallbackSupplier);
    }

    protected String safeStreamChat(String requestId, String prompt, StreamingChunkHandler handler,
            Supplier<String> fallbackSupplier) {
        return llmClient.streamChat(requestId, prompt, handler, fallbackSupplier);
    }
}
