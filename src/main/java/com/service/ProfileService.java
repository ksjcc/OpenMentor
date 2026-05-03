package com.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProfileService {

    public Map<String, String> status() {
        return Map.of("module", "profile", "status", "ready");
    }
}
