package com.ai.agent.impl;

import org.springframework.stereotype.Component;

import com.ai.agent.ReviewPlannerAgent;
import com.ai.agent.support.AbstractSafeAgentSupport;
import com.ai.client.LlmClient;

@Component
public class ReviewPlannerAgentImpl extends AbstractSafeAgentSupport implements ReviewPlannerAgent {

  public ReviewPlannerAgentImpl(LlmClient llmClient) {
    super(llmClient);
  }

  @Override
  public String plan(String input) {
    return safeChat(
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
