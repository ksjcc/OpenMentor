package com.ai.rag;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridRetriever {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public HybridRetriever(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public List<String> retrieve(String query) {
        return vectorStoreService.search(embeddingService.embed(query));
    }
}
