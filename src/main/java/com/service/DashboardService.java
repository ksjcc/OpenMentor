package com.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DashboardService {

    public Map<String, String> status() {
        return Map.of("module", "dashboard", "status", "ready");
    }
}
