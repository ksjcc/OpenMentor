package com.ai.interview.tool;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ai.agent.ScoreAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.service.JsonParseService;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

@Component
public class ScoreAnswerTool implements AgentTool {

  private final ScoreAgent scoreAgent;
  private final PromptBuilder promptBuilder;
  private final JsonParseService jsonParseService;

  public ScoreAnswerTool(ScoreAgent scoreAgent, PromptBuilder promptBuilder, JsonParseService jsonParseService) {
    this.scoreAgent = scoreAgent;
    this.promptBuilder = promptBuilder;
    this.jsonParseService = jsonParseService;
  }

  @Override
  public String getName() {
    return "score_answer";
  }

  @Override
  public String getDescription() {
    return "给候选人本轮回答打分。每轮只调一次，调过之后直接用结果，不要重复调用。";
  }

  @Override
  public Map<String, Object> getParameters() {
    return Map.of("type", "object", "properties", Map.of());
  }

  @Override
  public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
    var ctx = param.getRuntimeContext();
    Object cached = ctx.get("score.observation");
    if (cached != null) {
      return Mono.just(ToolResultBlock.text(String.valueOf(cached)));
    }
    String question = String.valueOf(ctx.get("currentQuestion"));
    String answer = String.valueOf(ctx.get("currentAnswer"));
    String topic = String.valueOf(ctx.get("topic"));

    String scoreJson = scoreAgent.score(promptBuilder.buildScorePrompt(PromptContext.builder()
        .currentQuestion(question)
        .currentAnswer(answer)
        .topic(topic)
        .build()));
    var scoreResult = jsonParseService.parseScore(scoreJson);
    String observation = "总分：" + scoreResult.getScore()
        + "（技术：" + scoreResult.getTechnical()
        + "，完整：" + scoreResult.getCompleteness()
        + "，表达：" + scoreResult.getExpression() + "）"
        + "；薄弱点：" + scoreResult.getWeakPoints()
        + "；评价：" + scoreResult.getFeedback();

    ctx.put("score.observation", observation);
    ctx.put("scoreResult", scoreResult);
    return Mono.just(ToolResultBlock.text(observation));
  }
}
