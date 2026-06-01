package com.service;

import com.dto.dashboard.LearningRecommendationDTO;
import com.service.Dashboard.LearningRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final LearningRecommendationService learningRecommendationService;

    public Map<String, String> status() {
        return Map.of("module", "review", "status", "ready");
    }

    public LearningRecommendationDTO generatePlan(Long userId) {
        return learningRecommendationService.generate(userId);
    }
}
