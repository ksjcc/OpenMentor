package com.ai.interview.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

@Component
public class MarkTurnActionTool implements AgentTool {
  private static final Set<String> ALLOWED = Set.of("FOLLOW_UP", "NEXT", "HARDER", "FINISH", "CLARIFY");
  private static final int MIN_ROUNDS_TO_FINISH = 6;

  @Override
  public String getName() {
    return "mark_turn_action";
  }

  @Override
  public String getDescription() {
    return "在给出最终回复之前调用，标记本轮动作：FOLLOW_UP(深挖) / NEXT(换题) / HARDER(升难度) "
        + "/ FINISH(结束面试) / CLARIFY(用户答非所问，要求展开)。";
  }

  @Override
  public Map<String, Object> getParameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "action", Map.of("type", "string",
                "enum", List.of("FOLLOW_UP", "NEXT", "HARDER", "FINISH", "CLARIFY"))),
        "required", List.of("action"));
  }

  @Override
  public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
    Object raw = param.getInput() == null ? null : param.getInput().get("action");
    String action = raw == null ? null : String.valueOf(raw).toUpperCase();
    if (action == null || !ALLOWED.contains(action)) {
      return Mono.just(ToolResultBlock.error("action 只能是 " + ALLOWED + " 之一，你给的是：" + raw));
    }

    var ctx = param.getRuntimeContext();
    int turn = ctx.get("turnIndex") == null ? 1 : Integer.parseInt(String.valueOf(ctx.get("turnIndex")));
    if ("FINISH".equals(action) && turn < MIN_ROUNDS_TO_FINISH) {
      return Mono.just(ToolResultBlock.error("现在才第 " + turn + " 轮，至少 " + MIN_ROUNDS_TO_FINISH
          + " 轮之后才能结束面试。请继续提问，动作选 FOLLOW_UP / NEXT / HARDER 之一。"));
    }

    ctx.put("turnAction", action);
    return Mono.just(ToolResultBlock.text("已记录本轮动作：" + action));
  }

}
