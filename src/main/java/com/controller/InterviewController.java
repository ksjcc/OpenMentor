package com.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.common.Result;
import com.dto.interview.InterviewReportDTO;
import com.dto.interview.InterviewSummaryDTO;
import com.service.InterviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/interview")
@Tag(name = "模拟面试", description = "模拟面试相关接口")
public class InterviewController {

        private final InterviewService interviewService;

        public InterviewController(InterviewService interviewService) {
                this.interviewService = interviewService;
        }

        /**
         * 开始一场面试。
         *
         * <p>
         * resumeId 指向简历库中的一份简历，会被注入面试官上下文；
         * 不传时使用该用户的默认简历（没有简历也能正常面试）。
         */
        @PostMapping("/start")
        @Operation(summary = "启动模拟面试")
        public SseEmitter startInterview(
                        @RequestParam Long userId,
                        @RequestParam String interviewType,
                        @RequestParam String targetRole,
                        @RequestParam String topic,
                        @RequestParam(required = false) String projectSummary,
                        @RequestParam(required = false) Long resumeId,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
                return interviewService.startInterviewStream(
                                userId,
                                interviewType,
                                targetRole,
                                topic,
                                projectSummary,
                                resumeId,
                                idempotencyKey);
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
        public SseEmitter answerQuestion(
                        @PathVariable Long sessionId,
                        @RequestParam Long userId,
                        @RequestParam(required = false) String topic,
                        @RequestBody String answer) {
                return interviewService.answerQuestionStream(
                                userId,
                                sessionId,
                                topic,
                                stripJsonQuotes(answer));
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

        /**
         * 我的面试记录。
         */
        @GetMapping
        @Operation(summary = "面试记录列表")
        public Result<List<InterviewSummaryDTO>> list(
                        @RequestParam Long userId,
                        @RequestParam(required = false) Integer limit) {
                return Result.success(interviewService.listInterviews(userId, limit));
        }

        /**
         * 单场复盘报告：对话、评分、知识提炼、闪卡都在这里。
         */
        @GetMapping("/{sessionId}")
        @Operation(summary = "面试复盘报告")
        public Result<InterviewReportDTO> report(
                        @PathVariable Long sessionId,
                        @RequestParam Long userId) {
                return Result.success(interviewService.getInterviewReport(userId, sessionId));
        }

        @PostMapping("/answer")
        @Operation(summary = "处理用户回答（无会话，兼容旧接口）")
        public Result<String> answer(
                        @RequestParam Long userId,
                        @RequestParam String topic,
                        @RequestBody String answer) {
                return Result.success(
                                interviewService.handleAnswer(userId, topic, stripJsonQuotes(answer)));
        }

        @PostMapping("/{sessionId}/stop")
        public Result<String> stopStream(@PathVariable Long sessionId, @RequestParam Long userId) {
                interviewService.stopStream(userId, sessionId);
                return Result.success("stopped");
        }

        /**
         * 前端历史上用 application/json 发送纯文本，body 会带上首尾引号。
         * 这里去掉一层引号，避免把 {@code "回答"} 原样写进对话记录。
         */
        private String stripJsonQuotes(String body) {
                if (body == null) {
                        return null;
                }
                String text = body.trim();
                if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                        String inner = text.substring(1, text.length() - 1);
                        return inner.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
                }
                return body;
        }
}
