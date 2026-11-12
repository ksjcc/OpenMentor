package com.ai.interview.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.entity.InterviewMessage;
import com.mapper.InterviewMessageMapper;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

@Component
public class ReadHistoryTool implements AgentTool {

  private final InterviewMessageMapper messageMapper;

  public ReadHistoryTool(InterviewMessageMapper messageMapper) {
    this.messageMapper = messageMapper;
  }

  @Override
  public String getName() {
    return "read_history";
  }

  @Override
  public String getDescription() {
    return "当你要确认之前问过什么、避免重复提问，或回顾候选人前几轮表现时使用。";
  }

  @Override
  public Map<String, Object> getParameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "rounds", Map.of("type", "integer", "description", "读取最近几轮，1-10，默认 3")),
        "required", List.of());
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
    var ctx = param.getRuntimeContext();
    String sessionId = ctx.getSessionId();
    int rounds = 3;
    Object raw = param.getInput() == null ? null : param.getInput().get("rounds");
    if (raw != null) {
      rounds = Math.min(Math.max(Integer.parseInt(String.valueOf(raw)), 1), 10);
    }
    List<InterviewMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
        .eq(InterviewMessage::getSessionId, sessionId)
        .orderByDesc(InterviewMessage::getId)
        .last("LIMIT " + (rounds * 2)));
    if (messages == null || messages.isEmpty()) {
      return Mono.just(ToolResultBlock.text("这是本场面试的第一轮，没有历史。"));
    }
    StringBuilder sb = new StringBuilder();
    for (int i = messages.size() - 1; i > 0; i--) {
      InterviewMessage m = messages.get(i);
      sb.append(m.getRole()).append("：").append(m.getContent()).append('\n');
    }
    return Mono.just(ToolResultBlock.text("最近 " + rounds + " 轮：\n" + sb));
  }
}
