package com.ai.agent;

import com.ai.Stream.StreamingChunkHandler;
import dev.langchain4j.service.UserMessage;

public interface TechInterviewerAgent {
    String streamQuestion(String requestId, @UserMessage String input, StreamingChunkHandler handler);
}
