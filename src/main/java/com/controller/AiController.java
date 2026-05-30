package com.controller;

import com.ai.client.LlmClient;
import com.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI调试", description = "底层大模型透传调试接口，不作为正式产品入口")
public class AiController {
  private final LlmClient llmClient;

  @PostMapping("/chat")
  @Operation(summary = "原始模型调试对话")
  public Result<String> chat(@RequestBody String message) {
    return Result.success(llmClient.chat(message));
  }
}
