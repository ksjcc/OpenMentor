package com.ai.memory;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryRetriever {

    public List<String> retrieveByUserId(Long userId) {
        return List.of();
    }
}
