package com.ai.memory;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryRanker {

    public List<String> rank(List<String> memories) {
        return memories;
    }
}
