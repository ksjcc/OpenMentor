package com.ai.orchestrator;

import com.ai.agent.FollowUpAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.ScoreAgent;
import com.ai.agent.TechInterviewerAgent;
import com.ai.decision.DifficultyPolicy;
import com.ai.decision.DecisionAggregator;
import com.ai.decision.GraphDecisionService;
import com.ai.decision.MemoryDecisionService;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import com.ai.rag.HybridRetriever;
import com.common.GraphRagMode;
import com.dto.InterviewReplyDTO;
import com.dto.decision.AgentDecisionDTO;
import com.dto.decision.FinalDecisionDTO;
import com.dto.graph.GraphReasoningResult;
import com.dto.score.ScoreResultDTO;
import com.entity.InterviewMessage;
import com.service.JsonParseService;
import com.service.MemoryService;
import com.service.cache.InterviewMemoryCacheService;
import com.service.garphrag.GraphRagRetriever;
import com.service.graph.GraphReasoningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentOrchestrator
 *
 * 多 Agent 协作决策中控层。
 */
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {
    private final TechInterviewerAgent techInterviewerAgent;
    private final ScoreAgent scoreAgent;
    private final FollowUpAgent followUpAgent;
    private final KnowledgeExtractorAgent knowledgeExtractorAgent;
    private final PromptBuilder promptBuilder;
    private final MemoryService memoryService;
    private final HybridRetriever hybridRetriever;
    private final ObjectMapper objectMapper;
    private final GraphReasoningService graphReasoningService;
    private final JsonParseService jsonParseService;
    private final DifficultyPolicy difficultyPolicy;
    private final GraphDecisionService graphDecisionService;
    private final MemoryDecisionService memoryDecisionService;
    private final InterviewMemoryCacheService interviewMemoryCacheService;
    private final GraphRagRetriever graphRagRetriever;
    private final DecisionAggregator decisionAggregator;

    /**
     * 生成面试第一题
     *
     * @param userId 用户ID
     * @param topic  当前面试主题，例如 Redis、MySQL、JVM
     * @return AI生成的问题
     */
    public String generateFirstQuestion(Long userId, String topic) {
        // 1. 读取 JSON 结构化长期记忆
        String profileJson = memoryService.getLastProfileJson(userId);

        // 2. 读取 Markdown 长期总结
        String summaryMd = memoryService.getLastSummaryMd(userId, "interview-extraction");

        // 3. 检索与当前主题相关的历史记忆
        List<String> relatedMemories = hybridRetriever.retrieve(userId, topic, 5);

        // 4. 构造 Agent 输入
        String input = promptBuilder.buildQuestionPrompt(PromptContext.builder()
                .userId(userId)
                .profileJson(profileJson)
                .summaryMd(summaryMd)
                .ragMemories(relatedMemories)
                .topic(topic)
                .task(PromptTemplates.TASK_NEW_QUESTION)
                .build());

        // 5. 调用面试官 Agent 生成问题
        return techInterviewerAgent.askQuestion(input);
    }

    /**
     * 处理用户回答：
     * 用户回答 → 评分 → 判断是否追问 → 生成下一句话
     *
     * @param userId   用户ID
     * @param topic    当前主题
     * @param question 当前AI问题
     * @param answer   用户回答
     * @return 编排后的AI回复
     */

    /**
     * 处理用户回答，并把 Redis 短期记忆注入 Agent Prompt。
     *
     * @param userId         用户ID
     * @param topic          当前主题
     * @param question       当前AI问题
     * @param answer         用户回答
     * @param recentMessages Redis 中最近 20 条面试上下文，按时间从旧到新排列
     * @return 编排后的AI回复
     */
    public InterviewReplyDTO HandleAnswer(
            Long userId,
            String topic,
            String question,
            String answer,
            List<InterviewMessage> recentMessages) {
        InterviewReplyDTO reply = new InterviewReplyDTO();
        String recentContext = buildRecentContext(recentMessages);
        PromptContext baseContext = buildInterviewContext(
                userId,
                topic,
                question,
                answer,
                recentContext);

        // 1. 调用评分 Agent 评分
        String scoreInput = promptBuilder.buildScorePrompt(baseContext);
        String scoreJson = scoreAgent.score(scoreInput);
        reply.setScoreJson(scoreJson);
        // 2.追问Agent
        PromptContext scoredContext = baseContext.toBuilder()
                .scoreJson(scoreJson)
                .build();
        String decisionInput = promptBuilder.buildDecisionPrompt(scoredContext);
        String decisionJson = followUpAgent.decide(decisionInput);
        String action = parseDecisionAction(decisionJson);
        // 3. 根据决策结果生成下一步问题
        if ("FOLLOW_UP".equals(action)) {

            String followUpInput = promptBuilder.buildFollowUpPrompt(scoredContext);
            String followUpQuestion = techInterviewerAgent.askQuestion(followUpInput);
            reply.setAction("FOLLOW_UP");
            reply.setContent(followUpQuestion);
            return reply;
        }
        if ("HARDER".equals(action)) {

            String harderInput = promptBuilder.buildHarderQuestionPrompt(scoredContext);
            String harderQuestion = techInterviewerAgent.askQuestion(harderInput);

            reply.setAction("QUESTION");
            reply.setContent(harderQuestion);
            return reply;
        }
        // 默认进入下一题
        String nextQuestion = generateFirstQuestion(userId, topic);
        reply.setAction("NEXT");
        reply.setContent(nextQuestion);
        return reply;
    }

    public InterviewReplyDTO HandleAnswer(
            Long userId,
            String topic,
            String question,
            String answer) {
        return HandleAnswer(userId, topic, question, answer, List.of());
    }

    private String buildRecentContext(List<InterviewMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "暂无短期上下文。";
        }

        StringBuilder context = new StringBuilder();
        for (InterviewMessage message : recentMessages) {
            context.append(message.getRole())
                    .append("（")
                    .append(message.getAgentCode())
                    .append("）：")
                    .append(message.getContent())
                    .append("\n");
        }
        return context.toString();
    }

    /**
     * 面试结束后进行知识提炼
     *
     * @param dialogue 本场面试完整对话文本
     * @return 知识提炼 JSON
     */
    public String extractKnowledge(String dialogue) {
        String input = promptBuilder.buildKnowledgeExtractionPrompt(PromptContext.builder()
                .dialogue(dialogue)
                .task(PromptTemplates.TASK_EXTRACTION)
                .build());
        return knowledgeExtractorAgent.extract(input);
    }

    private PromptContext buildInterviewContext(
            Long userId,
            String topic,
            String question,
            String answer,
            String recentContext) {
        String profileJson = memoryService.getLastProfileJson(userId);
        String summaryMd = memoryService.getLastSummaryMd(userId, "interview-extraction");
        List<String> relatedMemories = hybridRetriever.retrieve(userId, topic, 5);

        return PromptContext.builder()
                .userId(userId)
                .profileJson(profileJson)
                .summaryMd(summaryMd)
                .ragMemories(relatedMemories)
                .recentContext(recentContext)
                .currentQuestion(question)
                .currentAnswer(answer)
                .topic(topic)
                .build();
    }

    private String parseDecisionAction(String decisionJson) {
        if (decisionJson == null || decisionJson.isBlank()) {
            return "NEXT";
        }

        try {
            JsonNode root = objectMapper.readTree(normalizeJson(decisionJson));
            String action = root.path("action").asText("NEXT").trim();
            if ("FOLLOW_UP".equals(action) || "HARDER".equals(action) || "NEXT".equals(action)) {
                return action;
            }
        } catch (Exception ignored) {
            if (decisionJson.contains("FOLLOW_UP")) {
                return "FOLLOW_UP";
            }
            if (decisionJson.contains("HARDER")) {
                return "HARDER";
            }
        }

        return "NEXT";
    }

    private String normalizeJson(String json) {
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    /**
     * 生成追问问题
     *
     * AI流程：
     *
     * 用户回答
     * ↓
     * ScoreAgent评分
     * ↓
     * Graph推理
     * ↓
     * Memory召回
     * ↓
     * PromptBuilder
     * ↓
     * FollowUpAgent
     */
    public String generateFollowUpQuestion(
            Long userId,
            Long sessionId,
            String question,
            String answer,
            String profileJson,
            String summaryMd) {

        // =========================
        // 1. ScoreAgent 评分
        // =========================
        String scorePrompt = promptBuilder.buildScorePrompt(
                PromptContext.builder()
                        .currentQuestion(question)
                        .currentAnswer(answer)
                        .build());

        String scoreJson = scoreAgent.score(scorePrompt);

        // =========================
        // 2. 解析评分结果
        // =========================
        ScoreResultDTO scoreResult = jsonParseService.parseScore(
                scoreJson);

        // =========================
        // 3. 提取 weakPoint
        // =========================
        String weakTopic = null;

        if (scoreResult.getWeakPoints() != null
                && !scoreResult.getWeakPoints().isEmpty()) {

            weakTopic = scoreResult.getWeakPoints().get(0);
        }

        // =========================
        // 4. Graph 推理
        // =========================
        GraphReasoningResult graphResult = null;

        if (weakTopic != null) {

            graphResult = graphReasoningService
                    .reasonWeakness(
                            userId,
                            weakTopic);
        }

        // =========================
        // 5. Redis短期记忆
        // =========================
        String recentContext = interviewMemoryCacheService
                .buildtContext(
                        sessionId);

        // =========================
        // 6. graphrag记忆
        // =========================
        List<String> graphRagContext = graphRagRetriever.retrieve(userId, question + "\n" + answer, GraphRagMode.AUTO);

        List<String> ragMemories = memoryService.retrieveRelevantMemories(
                userId,
                weakTopic == null
                        ? question
                        : weakTopic);

        // =========================
        // 7. 构建 PromptContext
        // =========================
        PromptContext context = PromptContext.builder()
                .userId(userId)
                .sessionId(sessionId)

                // Profile
                .profileJson(profileJson)

                // 长期 Markdown总结
                .summaryMd(summaryMd)

                // Redis短期记忆
                .recentContext(recentContext)

                // PGVector长期记忆
                .ragMemories(ragMemories)

                // 当前问题
                .currentQuestion(question)

                // 用户回答
                .currentAnswer(answer)

                // score
                .scoreJson(scoreJson)

                // graph reasoning
                .graphReasoning(
                        graphResult == null
                                ? null
                                : graphResult.getReasoning())

                .build();

        // =========================
        // 8. PromptBuilder
        // =========================
        String followUpPrompt = promptBuilder.buildFollowUpPrompt(
                context);

        // =========================
        // 9. FollowUpAgent
        // =========================
        return followUpAgent.askFollowUp(
                followUpPrompt);
    }

    /**
     * 多 Agent 协作处理用户回答
     *
     * @param userId      用户ID
     * @param sessionId   面试ID
     * @param topic       当前主题
     * @param question    当前问题
     * @param answer      用户回答
     * @param profileJson 用户画像
     * @param summaryMd   长期总结
     * @return AI 下一步回复
     */
    public OrchestratorResult handleAnswerWithMultiAgentDecision(
            Long userId,
            Long sessionId,
            String topic,
            String question,
            String answer,
            String profileJson,
            String summaryMd,
            List<String> graphRagContext) {

        // =========================
        // 1. 构建评分 Prompt
        // =========================
        PromptContext scoreContext = PromptContext.builder()
                .currentQuestion(question)
                .currentAnswer(answer)
                .topic(topic)
                .build();

        String scorePrompt = promptBuilder.buildScorePrompt(scoreContext);

        // =========================
        // 2. ScoreAgent 评分
        // =========================
        String scoreJson = scoreAgent.score(scorePrompt);

        ScoreResultDTO scoreResult = jsonParseService.parseScore(scoreJson);

        // =========================
        // 3. 获取主要薄弱点
        // =========================
        String weakTopic = null;

        if (scoreResult.getWeakPoints() != null
                && !scoreResult.getWeakPoints().isEmpty()) {
            weakTopic = scoreResult.getWeakPoints().get(0);
        }

        if (weakTopic == null || weakTopic.isBlank()) {
            weakTopic = topic;
        }

        // =========================
        // 4. 多模块并行/顺序给出决策建议
        // =========================
        List<AgentDecisionDTO> decisions = new ArrayList<>();

        decisions.add(
                difficultyPolicy.decide(scoreResult));

        decisions.add(
                memoryDecisionService.decide(userId, weakTopic));

        decisions.add(
                graphDecisionService.decide(userId, weakTopic));

        // =========================
        // 5. 聚合最终决策
        // =========================
        FinalDecisionDTO finalDecision = decisionAggregator.aggregate(decisions);

        // =========================
        // 6. 准备上下文：Redis短期记忆
        // =========================
        String recentContext = interviewMemoryCacheService.buildRecentContext(sessionId);

        // =========================
        // 7. 准备上下文：PGVector长期记忆
        // =========================
        /*
         * List<String> ragMemories = memoryService.retrieveRelevantMemories(
         * userId,
         * finalDecision.getTopic() == null
         * ? weakTopic
         * : finalDecision.getTopic());
         */
        // =========================
        // 8. 构建统一 PromptContext
        // =========================
        PromptContext context = PromptContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .profileJson(profileJson)
                .summaryMd(summaryMd)
                .ragMemories(graphRagContext)
                .recentContext(recentContext)
                .currentQuestion(question)
                .currentAnswer(answer)
                .scoreJson(scoreJson)
                .topic(finalDecision.getTopic() == null ? topic : finalDecision.getTopic())
                .graphReasoning(finalDecision.getReason())
                .build();

        // =========================
        // 9. 根据最终决策调用对应 Agent
        // =========================
        String nextContent;

        switch (finalDecision.getAction()) {

            case FOLLOW_UP -> {
                String prompt = promptBuilder.buildFollowUpPrompt(context);
                nextContent = followUpAgent.askFollowUp(prompt);
            }

            case HARDER -> {
                String prompt = promptBuilder.buildHarderQuestionPrompt(context);
                nextContent = techInterviewerAgent.askQuestion(prompt);
            }

            case NEXT -> {
                String prompt = promptBuilder.buildQuestionPrompt(context);
                nextContent = techInterviewerAgent.askQuestion(prompt);
            }

            case FINISH -> {
                nextContent = "本轮面试已完成，接下来将为你生成面试报告和复习建议。";
            }

            default -> {
                String prompt = promptBuilder.buildQuestionPrompt(context);
                nextContent = techInterviewerAgent.askQuestion(prompt);
            }
        }

        // =========================
        // 10. 返回 Orchestrator 结果
        // =========================
        OrchestratorResult result = new OrchestratorResult();

        result.setAction(finalDecision.getAction().name());
        result.setContent(nextContent);
        result.setScoreJson(scoreJson);
        result.setDecisionReason(finalDecision.getReason());
        result.setAgentDecisions(decisions);

        return result;
    }
}
