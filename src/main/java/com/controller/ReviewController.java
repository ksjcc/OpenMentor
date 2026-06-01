package com.controller;

import com.common.Result;
import com.service.ReviewService;
import com.service.Dashboard.FlashcardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/review")
@Tag(name = "复盘计划", description = "面试复盘和训练计划相关接口")
public class ReviewController {

    private final ReviewService reviewService;
    private final FlashcardService flashcardService;

    public ReviewController(ReviewService reviewService, FlashcardService flashcardService) {
        this.reviewService = reviewService;
        this.flashcardService = flashcardService;
    }

    @GetMapping("/today")
    public Result<?> today(@RequestParam Long userId) {
        return Result.success(flashcardService.getTodayReview(userId));
    }

    /**
     * 提交复习结果
     */
    @PostMapping("/submit")
    public Result<?> submit(
            @RequestParam Long cardId,
            @RequestParam boolean correct) {
        flashcardService.review(cardId, correct);
        return Result.success(Map.of("message", "提交成功"));
    }
}
