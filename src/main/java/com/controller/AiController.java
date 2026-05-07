package com.controller;
import com.ai.client.LlmClient;
import com.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
 private final LlmClient llmClient;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody String message) {
        return Result.success(llmClient.chat(message));
    }
}
