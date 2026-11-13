package com.ai.interview.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ai.memory.MemoryLayer;
import com.ai.memory.MemoryStorageSelectorService;
import com.ai.rag.HybridRetriever;
import com.ai.rag.RetrievalRequest;
import com.common.GraphRagMode;
import com.dto.Retrieval.QueryType;
import com.dto.Retrieval.RetrievalComplexity;
import com.dto.Retrieval.RetrievalPlanDTO;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

/**
 * 检索工具：由模型显式选择"检索范围 + 深度"，再翻译成规范化的
 * {@link RetrievalPlanDTO} 交给 {@link HybridRetriever}。
 *
 * <p>
 * 为什么不让模型直接填 {@link RetrievalPlanDTO}：
 * 它有 20 个字段，其中 queryType / complexity / storage 之间还有耦合。
 * 对模型暴露的只有三个决策维度：范围（scopes）、深度（depth）、记忆层（memoryLayer）。
 */
@Component
public class RetrieveMaterialTool implements AgentTool {

  private static final int MAX_QUERY_CHARS = 240;
  private static final int MIN_TOPK = 1;
  private static final int MAX_TOPK = 8;

  private final HybridRetriever hybridRetriever;
  private final MemoryStorageSelectorService memoryStorageSelectorService;

  public RetrieveMaterialTool(
      HybridRetriever hybridRetriever,
      MemoryStorageSelectorService memoryStorageSelectorService) {
    this.hybridRetriever = hybridRetriever;
    this.memoryStorageSelectorService = memoryStorageSelectorService;
  }

  @Override
  public String getName() {
    return "retrieve_material";
  }

  @Override
  public String getDescription() {
    return """
        检索面试需要的证据材料。**先选范围，再检索**，不同范围代价和用途完全不同：

        - MEMORY：该用户的历史面试、长期记忆、画像。用于判断"他是不是真的做过""以前哪里薄弱"。
          配 memoryLayer：
            SESSION    = 本场面试刚说过的话（追问上一题时用）
            GROWTH     = 历次面试的沉淀、薄弱点、复习记录
            PREFERENCE = 目标岗位、技术栈、简历画像
        - VECTOR：通用技术知识库，用于拿标准说法/对照资料。
        - GRAPH：知识点之间的关系与多跳（例如"分布式锁"关联到"幂等""主从切换"）。
          配 graphMode：LOCAL(从一个实体看邻居) / GLOBAL(看整体主题分布) / DRIFT(顺着主题找更远的关联) / AUTO。

        深度 depth：
          LIGHT  = 2 条，快速确认，不确定时才用
          NORMAL = 4 条，常规
          DEEP   = 6 条 + 查询扩展，要拿全貌时才用

        调用纪律：
        - query 只放具体技术名词或候选人原话里的关键短语，不要放整段回答
        - 同一个知识点在同一轮不要重复检索
        - 拿到证据后要在后续回复里引用编号（如 [ev-2]），不要编造没检索到的内容
        """;
  }

  @Override
  public Map<String, Object> getParameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "query", Map.of(
                "type", "string",
                "description", "检索语句。用具体技术名词或候选人原话里的关键短语，最多 240 字。"),
            "scopes", Map.of(
                "type", "array",
                "items", Map.of("type", "string", "enum", List.of("MEMORY", "VECTOR", "GRAPH")),
                "description", "要检索的数据源，可多选。第一个元素作为主检索器。"),
            "memoryLayer", Map.of(
                "type", "string",
                "enum", List.of("SESSION", "GROWTH", "PREFERENCE"),
                "description", "scopes 含 MEMORY 时生效。默认 GROWTH。"),
            "graphMode", Map.of(
                "type", "string",
                "enum", List.of("LOCAL", "GLOBAL", "DRIFT", "AUTO"),
                "description", "scopes 含 GRAPH 时生效。默认 AUTO。"),
            "depth", Map.of(
                "type", "string",
                "enum", List.of("LIGHT", "NORMAL", "DEEP"),
                "description", "检索深度，决定条数与是否做查询扩展。默认 NORMAL。"),
            "topK", Map.of(
                "type", "integer",
                "description", "覆盖 depth 的条数，取值 1-8。一般不用传，让 depth 决定即可。"),
            "useHyde", Map.of(
                "type", "boolean",
                "description", "是否做查询扩展（把短查询改写成更易召回的表述）。DEEP 默认开。"),
            "queryType", Map.of(
                "type", "string",
                "enum", List.of(
                    "MEMORY", "KNOWLEDGE", "SYSTEM_DESIGN", "INTERVIEW_FOLLOWUP", "LEARNING", "MIXED"),
                "description", "检索意图。追问上一题时传 INTERVIEW_FOLLOWUP，会影响记忆分层路由。"),
            "contextDependent", Map.of(
                "type", "boolean",
                "description", "这次检索是否依赖本轮上下文（例如「刚才那个方案」）。")),
        "required", List.of("query", "scopes"));
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
    RuntimeContext ctx = param.getRuntimeContext();
    Long userId = asLong(ctx == null ? null : ctx.getUserId());
    if (userId == null) {
      return Mono.just(ToolResultBlock.error("缺少 userId，无法检索。"));
    }
    Long sessionId = asLong(ctx.getSessionId());

    RetrievalRequest request;
    try {
      request = parseRequest(param.getInput());
    } catch (IllegalArgumentException ex) {
      return Mono.just(ToolResultBlock.error(ex.getMessage()));
    }

    RetrievalPlanDTO plan = toPlan(request);
    String planLine = renderPlan(plan);

    try {
      List<String> hits = hybridRetriever.retrieve(userId, sessionId, plan, null);
      if (ctx != null) {
        ctx.put("retrieval.lastPlan", planLine);
      }
      return Mono.just(ToolResultBlock.text(renderHits(planLine, hits)));
    } catch (Exception ex) {
      return Mono.just(ToolResultBlock.error(
          planLine + "\n检索执行失败：" + ex.getClass().getSimpleName() + ": " + ex.getMessage()
              + "\n可以换一个更具体的关键词重试，或缩小 scopes。"));
    }
  }

  // ==================== 参数解析与校验 ====================

  private RetrievalRequest parseRequest(Map<String, Object> input) {
    String query = string(input, "query");
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException(
          "query 不能为空。请给一个具体知识点，例如 \"Redis 主从切换 数据丢失\"。");
    }
    query = query.trim();
    if (query.length() > MAX_QUERY_CHARS) {
      throw new IllegalArgumentException(
          "query 太长（" + query.length() + " 字）。只保留关键短语，最多 " + MAX_QUERY_CHARS + " 字。");
    }

    Set<RetrievalRequest.Scope> scopes = parseScopes(input.get("scopes"));
    if (scopes.isEmpty()) {
      throw new IllegalArgumentException(
          "scopes 不能为空。可选 MEMORY / VECTOR / GRAPH；不确定就传 [\"MEMORY\",\"VECTOR\"]。");
    }

    RetrievalRequest.Depth depth = RetrievalRequest.Depth.parse(string(input, "depth"),
        RetrievalRequest.Depth.NORMAL);

    Integer topK = integer(input, "topK");
    if (topK != null && (topK < MIN_TOPK || topK > MAX_TOPK)) {
      throw new IllegalArgumentException(
          "topK 超出范围：" + topK + "。只能取 " + MIN_TOPK + "-" + MAX_TOPK + "，或干脆不传让 depth 决定。");
    }

    MemoryLayer layer = RetrievalRequest.parseLayer(string(input, "memoryLayer"));
    Boolean useHyde = bool(input, "useHyde");
    GraphRagMode graphMode = RetrievalRequest.parseGraphMode(string(input, "graphMode"));
    QueryType queryType = RetrievalRequest.parseQueryType(string(input, "queryType"));
    boolean contextDependent = Boolean.TRUE.equals(bool(input, "contextDependent"));

    // 没给深度但有 memoryLayer=GROWTH 时，值得多拿一点
    if (depth == RetrievalRequest.Depth.LIGHT && scopes.size() > 1) {
      depth = RetrievalRequest.Depth.NORMAL;
    }

    return new RetrievalRequest(query, scopes, layer, graphMode, depth, topK, useHyde, queryType,
        contextDependent);
  }

  private Set<RetrievalRequest.Scope> parseScopes(Object raw) {
    if (raw == null) {
      return Set.of();
    }
    Object candidates = raw;
    if (!(raw instanceof Iterable)) {
      candidates = List.of(raw);
    }
    return RetrievalRequest.parseScopes((Iterable<?>) candidates);
  }

  // ==================== 计划构建 ====================

  private RetrievalPlanDTO toPlan(RetrievalRequest request) {
    boolean needMemory = request.needs(RetrievalRequest.Scope.MEMORY);
    boolean needVector = request.needs(RetrievalRequest.Scope.VECTOR);
    boolean needGraph = request.needs(RetrievalRequest.Scope.GRAPH);

    MemoryLayer layer = needMemory
        ? (request.memoryLayer() == null ? MemoryLayer.GROWTH : request.memoryLayer())
        : null;
    MemoryLayer fallbackLayer = layer == null ? null : fallbackFor(layer);

    GraphRagMode graphMode = needGraph
        ? (request.graphMode() == null ? GraphRagMode.AUTO : request.graphMode())
        : null;

    int topK = request.topK() != null ? request.topK() : defaultTopK(request.depth());
    boolean hyde = request.useHyde() != null
        ? request.useHyde()
        : request.depth() == RetrievalRequest.Depth.DEEP && needVector;

    RetrievalPlanDTO plan = RetrievalPlanDTO.builder()
        .primaryRetriever(primaryRetriever(request.primaryScope()))
        .needMemory(needMemory)
        .needVector(needVector)
        .needGraph(needGraph)
        .needHyde(hyde)
        .enableKeywordFallback(true)
        .graphMode(graphMode)
        .topK(topK)
        .query(request.query())
        .queryType(request.queryType())
        .complexity(resolveComplexity(request))
        .contextDependent(request.contextDependent())
        .memoryLayer(layer)
        .fallbackMemoryLayer(fallbackLayer)
        .reason("model-selected scopes=" + scopeNames(request)
            + " depth=" + request.depth()
            + (layer == null ? "" : " layer=" + layer)
            + (graphMode == null ? "" : " graphMode=" + graphMode))
        .build();

    // HybridRetriever 只有在 selectedStorage 非空时才采纳我们指定的记忆层，
    // 否则会退回关键词路由 —— 这里显式把层映射成具体存储，保证模型的意图生效。
    if (needMemory && layer != null) {
      plan.setSelectedStorage(memoryStorageSelectorService.select(layer, plan));
      plan.setFallbackStorage(memoryStorageSelectorService.select(fallbackLayer, plan));
    }

    return plan;
  }

  private MemoryLayer fallbackFor(MemoryLayer layer) {
    return switch (layer) {
      case SESSION -> MemoryLayer.GROWTH;
      case GROWTH -> MemoryLayer.PREFERENCE;
      case PREFERENCE -> MemoryLayer.GROWTH;
    };
  }

  private String primaryRetriever(RetrievalRequest.Scope scope) {
    return switch (scope) {
      case MEMORY -> "MEMORY_RAG";
      case GRAPH -> "GRAPH_RAG";
      case VECTOR -> "VECTOR_RAG";
    };
  }

  private int defaultTopK(RetrievalRequest.Depth depth) {
    return switch (depth) {
      case LIGHT -> 2;
      case NORMAL -> 4;
      case DEEP -> 6;
    };
  }

  private RetrievalComplexity resolveComplexity(RetrievalRequest request) {
    if (request.needs(RetrievalRequest.Scope.GRAPH) && request.graphMode() == GraphRagMode.GLOBAL) {
      return RetrievalComplexity.GLOBAL;
    }
    if (request.depth() == RetrievalRequest.Depth.DEEP || request.scopes().size() > 1) {
      return RetrievalComplexity.MULTI_HOP;
    }
    return RetrievalComplexity.SIMPLE;
  }

  private String scopeNames(RetrievalRequest request) {
    return request.scopes().stream().map(Enum::name).toList().toString();
  }

  // ==================== 结果渲染 ====================

  private String renderPlan(RetrievalPlanDTO plan) {
    return "检索计划：primary=" + plan.getPrimaryRetriever()
        + " memory=" + plan.isNeedMemory()
        + " vector=" + plan.isNeedVector()
        + " graph=" + plan.isNeedGraph()
        + " layer=" + (plan.getMemoryLayer() == null ? "-" : plan.getMemoryLayer())
        + " graphMode=" + (plan.getGraphMode() == null ? "-" : plan.getGraphMode())
        + " depth=" + (plan.getTopK() != null && plan.getTopK() >= 6
            ? "DEEP"
            : plan.getTopK() != null && plan.getTopK() <= 2 ? "LIGHT" : "NORMAL")
        + " topK=" + plan.getTopK()
        + " hyde=" + plan.isNeedHyde();
  }

  private String renderHits(String planLine, List<String> hits) {
    if (hits == null || hits.isEmpty()) {
      return planLine + "\n命中 0 条。\n"
          + "可能原因：关键词太泛、该用户没有相关历史、或该数据源还没建索引。\n"
          + "建议：换更具体的技术名词重试一次；如果只是要通用技术背景，把 scopes 加上 VECTOR；"
          + "如果要的是知识点关联，加上 GRAPH。";
    }
    StringBuilder sb = new StringBuilder(planLine)
        .append("\n命中 ")
        .append(hits.size())
        .append(" 条（引用时请标注编号）：\n");
    for (int i = 0; i < hits.size(); i++) {
      sb.append("[ev-").append(i + 1).append("] ")
          .append(hits.get(i).replaceAll("\\s+", " ").trim())
          .append('\n');
    }
    return sb.toString();
  }

  // ==================== 取值工具 ====================

  private static String string(Map<String, Object> input, String key) {
    Object value = input == null ? null : input.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static Integer integer(Map<String, Object> input, String key) {
    Object value = input == null ? null : input.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + " 必须是整数，收到的是：" + value);
    }
  }

  private static Boolean bool(Map<String, Object> input, String key) {
    Object value = input == null ? null : input.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    return "true".equals(text) || "1".equals(text) || "yes".equals(text);
  }

  private static Long asLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value.trim());
    } catch (NumberFormatException ex) {
      // 临时会话 id（ephemeral-xxx）不是数字，SESSION 层检索会因此跳过，属预期
      return null;
    }
  }

  /** 保留给未来扩展：去重后的 scope 名称。 */
  static Set<String> distinctScopes(Set<RetrievalRequest.Scope> scopes) {
    Set<String> names = new LinkedHashSet<>();
    scopes.forEach(scope -> names.add(scope.name()));
    return names;
  }
}
