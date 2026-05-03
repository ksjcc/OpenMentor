package com.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReviewService {

    public Map<String, String> status() {
        return Map.of("module", "review", "status", "ready");
    }
}
