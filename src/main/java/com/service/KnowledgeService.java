package com.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KnowledgeService {

    public Map<String, String> status() {
        return Map.of("module", "knowledge", "status", "ready");
    }
}
