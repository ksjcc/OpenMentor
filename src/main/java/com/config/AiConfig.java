package com.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.7}") Double temperature) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    /**
     * Embedding 模型：用于把 MemoryChunk 转成向量
     *
     * 注意：
     * text-embedding-v3 是通义常用 embedding 模型之一。
     * 如果你的账号模型名不同，需要在阿里云 DashScope 控制台确认。
     */
    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${langchain4j.open-ai.embedding-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.embedding-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.embedding-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.embedding-model.dimensions:1024}") Integer dimensions) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .dimensions(dimensions)
                .build();
    }
}
