package com.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.common.Result;
import com.service.KnowledgeQaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识问答", description = "AI 技术成长 Copilot 的知识学习与技术问答入口")
public class KnowledgeController {
  private final KnowledgeQaService knowledgeQaService;

  @PostMapping("/ask")
  @Operation(summary = "知识问答")
  public Result<String> ask(
      @RequestParam Long userId,
      @RequestBody String question) {
    return Result.success(knowledgeQaService.answer(userId, question));
  }
}
