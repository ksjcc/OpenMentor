package com.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InterviewService {

    public Map<String, String> status() {
        return Map.of("module", "interview", "status", "ready");
    }
}
