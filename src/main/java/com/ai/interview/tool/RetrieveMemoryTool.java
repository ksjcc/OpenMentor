package com.ai.interview.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ai.rag.HybridRetriever;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

@Component
public class RetrieveMemoryTool implements AgentTool {
  private final HybridRetriever retriever;

  public RetrieveMemoryTool(HybridRetriever retriever) {
    this.retriever = retriever;
  }

  @Override
  public String getName() {
    return "retrieve_memory";
  }

  @Override
  public String getDescription() {
    return "当你需要判断候选人是否真的做过某项技术、或需要了解他过往在这个知识点上的表现时使用。"
        + "只给一个具体知识点，不要传整段回答。";
  }

  @Override
  public Map<String, Object> getParameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "query", Map.of("type", "string", "description", "具体知识点，例如 Redis 持久化")),
        "required", List.of("query"));
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public Mono<ToolResultBlock> callAsync(ToolCallParam param) {

    String query = str(param, "query");
    if (query == null || query.isBlank()) {
      return Mono.just(ToolResultBlock.error("query不能为空"));
    }
    Long userId = userId(param.getRuntimeContext());
    List<String> hits = retriever.retrieve(userId, query, 3);
    if (hits.isEmpty()) {
      return Mono.just(ToolResultBlock.error("没有检索到相关记忆"));
    }
    StringBuilder sb = new StringBuilder("检索到 ").append(hits.size()).append(" 条：\n");
    for (int i = 0; i < hits.size(); i++) {
      sb.append("[mem-").append(i + 1).append("] ").append(hits.get(i)).append('\n');
    }
    return Mono.just(ToolResultBlock.text(sb.toString()));

  }

  private static String str(ToolCallParam param, String key) {
    Object v = param.getInput() == null ? null : param.getInput().get(key);
    return v == null ? null : String.valueOf(v);
  }

  static Long userId(RuntimeContext ctx) {
    return ctx == null || ctx.getUserId() == null ? null : Long.valueOf(ctx.getUserId());
  }

}
