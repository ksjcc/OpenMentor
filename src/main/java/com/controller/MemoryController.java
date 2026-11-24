package com.controller;

import com.common.Result;
import com.service.MemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/memory")
@Tag(name = "长期记忆", description = "用户记忆相关接口")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/profile-json")
    @Operation(summary = "保存用户画像 JSON")
    public Result<?> saveProfileJson(@RequestParam Long userId,
                                     @RequestBody String json) {
        memoryService.saveProfileJson(userId, json);
        return Result.success(null);
    }

    @GetMapping("/profile-json")
    @Operation(summary = "获取用户画像 JSON")
    public Result<String> getProfileJson(@RequestParam Long userId) {
        return Result.success(memoryService.getLastProfileJson(userId));
    }

    @PostMapping("/summary-md")
    @Operation(summary = "保存用户 Markdown 总结")
    public Result<?> saveSummaryMd(@RequestParam Long userId,
                                   @RequestParam String summaryType,
                                   @RequestBody String content) {
        memoryService.saveSummaryMd(userId, summaryType, content);
        return Result.success(null);
    }

    @GetMapping("/summary-md")
    @Operation(summary = "获取用户 Markdown 总结")
    public Result<String> getSummaryMd(@RequestParam Long userId,
                                       @RequestParam String summaryType) {
        return Result.success(memoryService.getLastSummaryMd(userId, summaryType));
    }

    @PostMapping("/chunk")
    @Operation(summary = "保存语义记忆片段")
    public Result<?> saveChunk(@RequestParam Long userId,
                               @RequestParam String sourceType,
                               @RequestParam Long sourceId,
                               @RequestBody String content) {
        memoryService.saveChunk(userId, sourceType, sourceId, content);
        return Result.success(null);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索相关记忆片段")
    public Result<?> search(@RequestParam Long userId,
                            @RequestParam String keyword) {
        return Result.success(memoryService.searchRelevantChunks(userId, keyword, 5));
    }

    @GetMapping("/recent")
    @Operation(summary = "最近写入的记忆片段")
    public Result<?> recent(@RequestParam Long userId,
                            @RequestParam(required = false) Integer limit) {
        return Result.success(memoryService.recentChunks(userId, limit));
    }
}
