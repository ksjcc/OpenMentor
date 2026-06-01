package com.controller;

import com.common.Result;
import com.dto.InterviewReplyDTO;
import com.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview")
@Tag(name = "模拟面试", description = "模拟面试相关接口")
public class InterviewController {

        private final InterviewService interviewService;

        public InterviewController(InterviewService interviewService) {
                this.interviewService = interviewService;
        }

        @PostMapping("/start")
        @Operation(summary = "启动模拟面试")
        public Result<InterviewReplyDTO> startInterview(
                        @RequestParam Long userId,
                        @RequestParam String interviewType,
                        @RequestParam String targetRole,
                        @RequestParam String topic,
                        @RequestParam(required = false) String projectSummary) {
                return Result.success(
                                interviewService.startInterview(
                                                userId,
                                                interviewType,
                                                targetRole,
                                                topic,
                                                projectSummary));

        }

        /**
         * 用户回答问题
         *
         * 示例：
         * POST /api/interview/{sessionId}/answer?userId=1&topic=Redis
         * Body: 用户回答内容
         */
        @PostMapping("/{sessionId}/answer")
        @Operation(summary = "提交用户回答")
        public Result<InterviewReplyDTO> answerQuestion(
                        @PathVariable Long sessionId,
                        @RequestParam Long userId,
                        @RequestParam String topic,
                        @RequestBody String answer) {
                return Result.success(
                                interviewService.answerQuestion(
                                                userId,
                                                sessionId,
                                                topic,
                                                answer));
        }

        /**
         * 结束面试
         *
         * 示例：
         * POST /api/interview/{sessionId}/finish?userId=1
         */
        @PostMapping("/{sessionId}/finish")
        @Operation(summary = "结束模拟面试")
        public Result<String> finishInterview(
                        @PathVariable Long sessionId,
                        @RequestParam Long userId) {
                return Result.success(
                                interviewService.finishInterview(userId, sessionId));
        }

        // @GetMapping("/question")
        // @Operation(summary = "生成面试问题")
        // public Result<String> question(@RequestParam Long userId, @RequestParam
        // String topic) {
        // return Result.success(interviewservice.generateQuestion(userId, topic));
        // }

        @PostMapping("/answer")
        @Operation(summary = "处理用户回答")
        public Result<String> answer(
                        @RequestParam Long userId,
                        @RequestParam String topic,
                        @RequestBody String answer) {
                return Result.success(
                                interviewService.handleAnswer(userId, topic, answer));
        }
}
