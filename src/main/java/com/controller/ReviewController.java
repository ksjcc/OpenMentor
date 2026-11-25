package com.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.common.Result;
import com.dto.dashboard.LearningRecommendationDTO;
import com.dto.review.ReviewPracticeDTO;
import com.service.Dashboard.FlashcardService;
import com.service.ReviewService;
import com.service.review.ReviewPracticeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/review")
@Tag(name = "复盘计划", description = "面试复盘和训练计划相关接口")
public class ReviewController {

    private final ReviewService reviewService;
    private final FlashcardService flashcardService;
    private final ReviewPracticeService reviewPracticeService;

    public ReviewController(ReviewService reviewService, FlashcardService flashcardService,
                            ReviewPracticeService reviewPracticeService) {
        this.reviewService = reviewService;
        this.flashcardService = flashcardService;
        this.reviewPracticeService = reviewPracticeService;
    }

    @GetMapping("/today")
    @Operation(summary = "今天到期的闪卡（旧接口，保留兼容）")
    public Result<?> today(@RequestParam Long userId) {
        return Result.success(flashcardService.getTodayReview(userId));
    }

    @GetMapping("/plan")
    @Operation(summary = "生成学习计划")
    public Result<LearningRecommendationDTO> plan(@RequestParam Long userId) {
        return Result.success(reviewService.generatePlan(userId));
    }

    /** 每日练习计划：从今天起 N 天，每天多少题、已完成多少、正确率多少。 */
    @GetMapping("/calendar")
    @Operation(summary = "每日练习计划")
    public Result<List<ReviewPracticeDTO.Daily>> calendar(
            @RequestParam Long userId,
            @RequestParam(required = false) Integer days) {
        return Result.success(reviewPracticeService.calendar(userId, days));
    }

    /** 某一天的正式练习：题目列表 + 当天累计正确率。 */
    @GetMapping("/practice")
    @Operation(summary = "某一天的练习题")
    public Result<ReviewPracticeDTO.Detail> practice(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(reviewPracticeService.practice(userId, date));
    }

    /**
     * 提交复习结果。
     *
     * <p>
     * 带 userId 时走"每日练习"逻辑：记录练习日、返回当天累计正确率与错误次数。
     * 不带 userId 时保持旧行为（只更新间隔）。
     */
    @PostMapping("/submit")
    @Operation(summary = "提交复习结果")
    public Result<?> submit(
            @RequestParam Long cardId,
            @RequestParam boolean correct,
            @RequestParam(required = false) Long userId) {
        if (userId == null) {
            flashcardService.review(cardId, correct);
            return Result.success(Map.of("message", "提交成功"));
        }
        return Result.success(reviewPracticeService.submit(userId, cardId, correct));
    }

    /** 「关联知识点」：同主题的历史闪卡 + 知识图谱一跳邻居主题的闪卡。 */
    @GetMapping("/related")
    @Operation(summary = "关联知识点闪卡")
    public Result<ReviewPracticeDTO.RelatedGroup> related(
            @RequestParam Long userId,
            @RequestParam Long cardId) {
        return Result.success(reviewPracticeService.related(userId, cardId));
    }
}
