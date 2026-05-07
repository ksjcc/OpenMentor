package com.ai.client;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmClient {

    private final ChatLanguageModel chatModel;

    public String chat(String message) {
        return chatModel.generate(message);
    }
}
