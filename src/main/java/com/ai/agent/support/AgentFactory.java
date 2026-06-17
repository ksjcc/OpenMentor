package com.ai.agent.support;

import com.ai.Stream.StreamingChunkHandler;
import com.ai.agent.CommunityReportAgent;
import com.ai.agent.DriftQuestionAgent;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.GraphExtractionAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.ResumeProfileAgent;
import com.ai.agent.ReviewPlannerAgent;
import com.ai.agent.ScoreAgent;
import com.ai.agent.TechInterviewerAgent;
import com.ai.client.LlmClient;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;

    public TechInterviewerAgent createTechInterviewerAgent() {
        return new TechInterviewerAgent() {
            @Override
            public String streamQuestion(String requestId, String input, StreamingChunkHandler handler) {
                return llmClient.streamChat(
                        requestId,
                        input,
                        handler,
                        () -> "请你先介绍一下这个问题的核心概念，并结合项目经验展开说明。");
            }
        };
    }

    public ScoreAgent createScoreAgent() {
        return input -> llmClient.safeChat(
                input,
                () -> """
                        {
                          "score": 60,
                          "technical": 20,
                          "completeness": 20,
                          "expression": 20,
                          "isWeak": true,
                          "feedback": "fallback",
                          "weakPoints": []
                        }
                        """);
    }

    public FollowUpAgent createFollowUpAgent() {
        return new FollowUpAgent() {
            @Override
            public String streamFollowUp(String requestId, String prompt, StreamingChunkHandler handler) {
                return llmClient.streamChat(
                        requestId,
                        prompt,
                        handler,
                        () -> "你刚才的回答还不够完整。请补充说明核心原理、关键流程，以及在真实项目中你会如何处理这个问题。");
            }
        };
    }

    public KnowledgeExtractorAgent createKnowledgeExtractorAgent() {
        return interviewContent -> llmClient.safeChat(
                interviewContent,
                () -> """
                        {
                          "summary": "fallback",
                          "strengths": [],
                          "weaknesses": [],
                          "flashcards": [],
                          "memory": {
                            "profileUpdate": "",
                            "markdownSummary": "fallback",
                            "chunks": []
                          },
                          "nextPlan": []
                        }
                        """);
    }

    public ResumeProfileAgent createResumeProfileAgent() {
        return resumeText -> llmClient.safeChat(
                resumeText,
                () -> """
                        {
                          "targetRole": "",
                          "experienceLevel": "",
                          "techStack": [],
                          "projects": [],
                          "strengths": [],
                          "weaknesses": [],
                          "abilityMap": {},
                          "learningPreference": "fallback"
                        }
                        """);
    }

    public GraphExtractionAgent createGraphExtractionAgent() {
        return text -> {
            String prompt = promptBuilder.buildGraphExtractionPrompt(PromptContext.builder()
                    .task(PromptTemplates.TASK_GRAPH_EXTRACTION)
                    .rawInput(text)
                    .build());
            return llmClient.safeChat(
                    prompt,
                    () -> """
                            {
                              "entities": [],
                              "relationships": []
                            }
                            """);
        };
    }

    public CommunityReportAgent createCommunityReportAgent() {
        return input -> {
            String prompt = promptBuilder.buildCommunityReportPrompt(PromptContext.builder()
                    .task(PromptTemplates.TASK_COMMUNITY_REPORT)
                    .rawInput(input)
                    .build());
            return llmClient.safeChat(
                    prompt,
                    () -> """
                            {
                              "title": "fallback",
                              "summary": "fallback",
                              "findings": [],
                              "rankScore": 0.0
                            }
                            """);
        };
    }

    public DriftQuestionAgent createDriftQuestionAgent() {
        return input -> {
            String prompt = promptBuilder.buildDriftQuestionPrompt(PromptContext.builder()
                    .task(PromptTemplates.TASK_DRIFT_QUERY)
                    .rawInput(input)
                    .build());
            return llmClient.safeChat(
                    prompt,
                    () -> """
                            {
                              "queries": []
                            }
                            """);
        };
    }

    public ReviewPlannerAgent createReviewPlannerAgent() {
        return input -> llmClient.safeChat(
                input,
                () -> """
                        {
                          "title": "3天弱项巩固计划",
                          "summary": "优先围绕当前最薄弱的知识点进行短周期复习和表达训练。",
                          "totalDays": 3,
                          "dailyMinutes": 45,
                          "focusAreas": ["基础原理", "高频面试表达"],
                          "steps": [
                            {
                              "dayIndex": 1,
                              "focus": "基础原理回顾",
                              "task": "复习当前最弱知识点的核心概念，并用自己的话整理一版回答框架。",
                              "checkpoints": ["能解释核心概念", "能说出常见使用场景"],
                              "estimatedMinutes": 45
                            },
                            {
                              "dayIndex": 2,
                              "focus": "结合场景练习",
                              "task": "针对目标岗位常见场景，完成 2 道相关问答训练并补齐遗漏点。",
                              "checkpoints": ["每题都能说出关键步骤", "能指出常见误区"],
                              "estimatedMinutes": 45
                            },
                            {
                              "dayIndex": 3,
                              "focus": "输出与复盘",
                              "task": "使用闪卡和口述复盘完成一次模拟表达训练，整理仍不熟练的点。",
                              "checkpoints": ["能在 3 分钟内完整表达", "记录至少 2 个待强化点"],
                              "estimatedMinutes": 45
                            }
                          ],
                          "successCriteria": ["能独立讲清核心原理", "能完成一次较完整的模拟回答"]
                        }
                        """);
    }
}
