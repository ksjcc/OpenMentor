package com.ai.agent;

import com.ai.Stream.StreamingChunkHandler;
import dev.langchain4j.service.UserMessage;

public interface FollowUpAgent {
    String streamFollowUp(String requestId, @UserMessage String prompt, StreamingChunkHandler handler);
}
