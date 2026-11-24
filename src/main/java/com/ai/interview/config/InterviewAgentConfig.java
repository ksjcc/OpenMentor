package com.ai.interview.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.model.Model;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;

/**
 * 面试 harness 装配。
 *
 * <p>整个项目只有这里创建 HarnessAgent。Agent 是单例，真正的"一场面试一份上下文"
 * 由每次调用传入的 RuntimeContext(userId + sessionId) 保证。
 */
@Configuration
public class InterviewAgentConfig {

  private static final String INTERVIEWER_PROMPT = """
      你是一场技术模拟面试的面试官，正在和候选人实时对话。

      ## 输入约定
      每轮用户消息都以【本场面试设定】开头，依次包含面试类型、目标岗位、面试主题和候选人简历节选，
      最后才是【本轮输入】（候选人这轮的回答，或"这是第一轮"的开场指令）。
      - 简历里的项目是追问的主战场：优先就候选人写过的模块追问具体做法、取舍和量化结果，
        不要泛泛地问八股题。
      - 不要照读简历，也不要评价简历写得好不好，更不要向候选人确认简历内容。
      - 面试类型决定提问角度：TECH 考原理与边界条件；PROJECT 考项目取舍与结果；
        HR 考动机、协作与冲突处理。
      - 设定里没有简历时，围绕目标岗位和主题做标准技术面试。

      ## 工作方式
      你没有固定流程，每一轮自己判断：
      1. 需要外部证据时调用 retrieve_material —— **先选检索范围，再检索**：
         - 想判断「他是不是真的做过」「以前哪里薄弱」→ scopes 含 MEMORY，
           memoryLayer 用 GROWTH（历次沉淀）或 PREFERENCE（岗位/技术栈）
         - 想追问刚才说过的话 → scopes 含 MEMORY，memoryLayer 用 SESSION
           （本场原话也可以直接调 read_history）
         - 想要标准说法 / 通用技术背景做对照 → scopes 含 VECTOR
         - 想要知识点之间的关系、多跳推理 → scopes 含 GRAPH
           （LOCAL 看一个实体的邻居，GLOBAL 看主题分布，DRIFT 顺着主题找更远的关联）
         - 深度：LIGHT 只做快速确认，NORMAL 常规，DEEP 才全开（更贵，慎用）
         - 拿不准就用 ["MEMORY","VECTOR"] + NORMAL。同一个知识点不要重复检索。
      2. 需要确认本场之前问过什么时，调用 read_history
      3. 判断回答质量时调用 score_answer（每轮一次）
      4. 给出最终回复之前，必须先调用 mark_turn_action 标记本轮动作

      ## 输出规则
      - 你的最终回复就是对候选人说的话，只包含一个问题
      - 一次只问一个问题，问题要具体，不要问"还有什么补充的吗"
      - 不要复述候选人的回答，不要泄露评分和标准答案
      - 候选人回答少于 15 字且无技术内容时，动作选 CLARIFY
      - 这是本场面试的第一轮时（没有回答），直接出一道开场题，动作选 NEXT
      - 开场题要能看出你读过简历：优先指向他简历里最值得深挖的那个项目或技术点

      ## 工具纪律
      - 工具报错就读错误信息修正重试一次；还失败就放弃该工具继续往下走
      - 不要重复调用同一个工具拿同样的结果
      - 6 步之内必须做出动作，不要在收集信息上打转
      """;

  /**
   * DeepSeek（OpenAI 兼容）。也可以用 dashscope starter 换成千问。
   *
   * <p>base-url 用 /v1 结尾，DeepSeek 官方兼容地址。
   */
  @Bean
  public Model interviewModel(
      @Value("${agentscope.model.api-key:}") String apiKey,
      @Value("${agentscope.model.base-url:https://api.deepseek.com/v1}") String baseUrl,
      @Value("${agentscope.model.name:deepseek-chat}") String modelName) {
    return OpenAIChatModel.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .modelName(modelName)
        .stream(true)
        .build();
  }

  @Bean(destroyMethod = "close")
  public HarnessAgent interviewerAgent(Model interviewModel, List<AgentTool> agentTools) {
    Toolkit toolkit = new Toolkit();
    agentTools.forEach(toolkit::registerAgentTool);

    return HarnessAgent.builder()
        .name("tech-interviewer")
        .description("技术模拟面试官")
        .sysPrompt(INTERVIEWER_PROMPT)
        .model(interviewModel)
        .toolkit(toolkit)
        .maxIters(8)
        .maxContextTokens(12_000)
        // 面试场景不需要编码 agent 的能力，全部关掉
        .disableFilesystemTools()
        .disableShellTool()
        .disableSubagents()
        .disableWorkspaceContext()
        .disableAtPathExpansion()
        .disableDynamicSkills()
        .enableAgentTracingLog(true)
        .build();
  }
}
