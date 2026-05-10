package com.ai.orchestrator;

import com.ai.agent.FollowUpAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.ScoreAgent;
import com.ai.agent.TechInterviewerAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.rag.HybridRetriever;
import com.dto.InterviewReplyDTO;
import com.service.MemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 编排
 * 统一管理多个Agent的调用顺序
 * 根据评分结果决定是否追问
 * 将多个agent角色串联起来
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
        String input = promptBuilder.buildTechInterviewInputwithMemoryTexts(
                profileJson,
                summaryMd,
                relatedMemories,
                topic);

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
    public InterviewReplyDTO HandleAnswer(Long userId, String topic, String question, String answer) {
        InterviewReplyDTO reply = new InterviewReplyDTO();
        // 1. 调用评分 Agent 评分
        String scoreInput = """
                【面试主题】
                %s
                【面试问题】
                %s
                【用户回答】
                %s
                """.formatted(topic, question, answer);
        String scoreJson = scoreAgent.score(scoreInput);
        reply.setScoreJson(scoreJson);
        // 2.追问Agent
        String decisionInput = """
                【当前主题】
                %s

                【问题】
                %s

                【用户回答】
                %s

                【评分结果】
                %s

                请判断下一步是追问、换题还是提高难度。
                """.formatted(topic, question, answer, scoreJson);
        String decisionJson = followUpAgent.decide(decisionInput);
        String action = parseDecisionAction(decisionJson);
        // 3. 根据决策结果生成下一步问题
        if ("FOLLOW_UP".equals(action)) {

            String followUpInput = """
                    用户刚才回答得不完整，请围绕同一个问题继续追问。

                    【原问题】
                    %s

                    【用户回答】
                    %s

                    【评分结果】
                    %s

                    请只输出一个追问问题。
                    """.formatted(question, answer, scoreJson);
            String followUpQuestion = techInterviewerAgent.askQuestion(followUpInput);
            reply.setAction("FOLLOW_UP");
            reply.setContent(followUpQuestion);
            return reply;
        }
        if ("HARDER".equals(action)) {

            String harderInput = """
                    用户回答较好，请围绕当前主题提出一个更高难度的问题。

                    【当前主题】
                    %s

                    【用户回答】
                    %s

                    请只输出一个更深入的问题。
                    """.formatted(topic, answer);

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

    /**
     * 面试结束后进行知识提炼
     *
     * @param dialogue 本场面试完整对话文本
     * @return 知识提炼 JSON
     */
    public String extractKnowledge(String dialogue) {
        return knowledgeExtractorAgent.extract(dialogue);
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
}
