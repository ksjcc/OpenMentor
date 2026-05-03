package com.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MemoryService {

    public Map<String, String> status() {
        return Map.of("module", "memory", "status", "ready");
    }
}
