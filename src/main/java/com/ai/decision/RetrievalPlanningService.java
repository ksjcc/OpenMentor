package com.ai.decision;

import com.common.GraphRagMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dto.Retrieval.QueryType;
import com.dto.Retrieval.RetrievalComplexity;
import com.dto.Retrieval.RetrievalPlanDTO;
import com.ai.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalPlanningService {
  private static final String DEFAULT_REASON = "planner fallback to conservative retrieval rule";

  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;

  public RetrievalPlanDTO plan(String question, String answer, String weakTopic) {
    String query = buildQuery(question, answer, weakTopic);
    PlannerSignal signal = inferSignal(question, answer, weakTopic, query);
    QueryType queryType = normalizeQueryType(signal.intent());
    RetrievalComplexity complexity = normalizeComplexity(signal.complexity());
    boolean contextDependent = signal.contextDependent() || signal.needsHistoricalContext();
    RetrievalStrategy strategy = buildStrategy(queryType, complexity, signal);
    int budget = calculateBudget(
        complexity,
        strategy);
    RetrievalPlanDTO plan = RetrievalPlanDTO.builder()
        .query(query)
        .queryType(queryType)
        .complexity(complexity)
        .contextDependent(contextDependent)
        .primaryRetriever(
            strategy.primary())
        .needMemory(
            strategy.needMemory())
        .needVector(
            strategy.needVector())
        .needGraph(
            strategy.needGraph())
        .needHyde(
            strategy.needHyde())
        .enableKeywordFallback(
            strategy.keywordFallback())
        .graphMode(
            strategy.graphMode())
        .topK(
            strategy.topK())
        .retrievalBudget(
            budget)
        .reason(
            buildReason(
                signal,
                queryType,
                complexity,
                strategy))
        .build();
    return plan;
  }

  private String buildQuery(String question, String answer, String weakTopic) {
    if (weakTopic != null && !weakTopic.isBlank()) {
      return weakTopic;
    }
    String q = question == null ? "" : question;
    String a = answer == null ? "" : answer;
    return (q + "\n" + a).trim();
  }

  private PlannerSignal inferSignal(
      String question,
      String answer,
      String weakTopic,
      String query) {
    if (query == null || query.isBlank()) {
      return fallbackSignal(question, answer, weakTopic);
    }

    String prompt = """
        你是一个 Retrieval Planner，只负责理解检索意图，不负责决定最终检索策略。
        请根据输入输出一个 JSON 对象，不要输出 markdown，不要补充解释。

        JSON 字段要求：
        - intent: MEMORY | KNOWLEDGE | SYSTEM_DESIGN | INTERVIEW_FOLLOWUP | LEARNING | MIXED
        - complexity: SIMPLE | MULTI_HOP | GLOBAL
        - contextDependent: boolean
        - needsHistoricalContext: boolean
        - needsConceptRetrieval: boolean
        - needsGraphReasoning: boolean
        - reason: string

        判定原则：
        - intent 负责表达问题语义主类，不要根据实现细节做判断。
        - contextDependent 表示是否依赖当前轮之前的上下文、用户画像、历史表现。
        - needsHistoricalContext 表示是否值得检索 memory。
        - needsConceptRetrieval 表示是否需要检索通用技术知识或资料片段。
        - needsGraphReasoning 表示是否需要关系、多跳、全局架构或权衡信息。

        输入：
        question=%s
        answer=%s
        weakTopic=%s
        query=%s
        """.formatted(
        safe(question),
        safe(answer),
        safe(weakTopic),
        safe(query));

    String json = llmClient.safeChat(prompt, () -> "");
    PlannerSignal signal = parseSignal(json);
    return signal != null ? signal : fallbackSignal(question, answer, weakTopic);
  }

  private PlannerSignal parseSignal(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    try {
      JsonNode root = objectMapper.readTree(extractJson(raw));
      return new PlannerSignal(
          readText(root, "intent"),
          readText(root, "complexity"),
          root.path("contextDependent").asBoolean(false),
          root.path("needsHistoricalContext").asBoolean(false),
          root.path("needsConceptRetrieval").asBoolean(true),
          root.path("needsGraphReasoning").asBoolean(false),
          readText(root, "reason"));
    } catch (Exception e) {
      log.warn("failed to parse retrieval planner signal", e);
      return null;
    }
  }

  private PlannerSignal fallbackSignal(
      String question,
      String answer,
      String weakTopic) {
    if (weakTopic != null && !weakTopic.isBlank()) {
      return new PlannerSignal(
          QueryType.LEARNING.name(),
          RetrievalComplexity.SIMPLE.name(),
          false,
          true,
          true,
          false,
          DEFAULT_REASON);
    }

    if (answer != null && !answer.isBlank()) {
      return new PlannerSignal(
          QueryType.INTERVIEW_FOLLOWUP.name(),
          RetrievalComplexity.MULTI_HOP.name(),
          true,
          true,
          true,
          true,
          DEFAULT_REASON);
    }

    return new PlannerSignal(
        QueryType.KNOWLEDGE.name(),
        RetrievalComplexity.SIMPLE.name(),
        false,
        false,
        true,
        false,
        DEFAULT_REASON);
  }

  // 职责型 Planner。
  record RetrievalStrategy(
      String primary,
      boolean needMemory,
      boolean needVector,
      boolean needGraph,
      boolean needHyde,
      boolean keywordFallback,
      GraphRagMode graphMode,
      int topK) {
  }

  private RetrievalStrategy buildStrategy(
      QueryType type,
      RetrievalComplexity complexity,
      PlannerSignal signal) {
    boolean needMemory = signal.needsHistoricalContext()
        || signal.contextDependent()
        || type == QueryType.MEMORY
        || type == QueryType.LEARNING
        || type == QueryType.MIXED
        || type == QueryType.INTERVIEW_FOLLOWUP;
    boolean needGraph = signal.needsGraphReasoning()
        || type == QueryType.SYSTEM_DESIGN
        || complexity != RetrievalComplexity.SIMPLE;
    boolean needVector = signal.needsConceptRetrieval()
        || (!needMemory && !needGraph)
        || type == QueryType.KNOWLEDGE
        || type == QueryType.MIXED;

    String primary = choosePrimary(type, complexity, needMemory, needVector, needGraph);
    GraphRagMode graphMode = chooseGraphMode(type, complexity, signal.contextDependent());
    boolean needHyde = needVector && primary.equals("VECTOR_RAG");
    boolean keywordFallback = !"GRAPH_RAG".equals(primary);
    int topK = chooseTopK(primary, complexity);

    return new RetrievalStrategy(
        primary,
        needMemory,
        needVector,
        needGraph,
        needHyde,
        keywordFallback,
        graphMode,
        topK);
  }

  // Budget安全
  private int calculateBudget(
      RetrievalComplexity complexity,
      RetrievalStrategy strategy) {

    int budget = 1;

    if (strategy.needMemory())
      budget++;
    if (strategy.needGraph())
      budget++;

    if (complexity == RetrievalComplexity.GLOBAL) {
      budget++;
    }

    return Math.min(budget, 4);
  }

  private QueryType normalizeQueryType(String intent) {
    if (intent == null || intent.isBlank()) {
      return QueryType.KNOWLEDGE;
    }

    try {
      return QueryType.valueOf(intent.trim().toUpperCase());
    } catch (Exception e) {
      return QueryType.KNOWLEDGE;
    }
  }

  private RetrievalComplexity normalizeComplexity(String complexity) {
    if (complexity == null || complexity.isBlank()) {
      return RetrievalComplexity.SIMPLE;
    }

    try {
      return RetrievalComplexity.valueOf(complexity.trim().toUpperCase());
    } catch (Exception e) {
      return RetrievalComplexity.SIMPLE;
    }
  }

  private String choosePrimary(
      QueryType type,
      RetrievalComplexity complexity,
      boolean needMemory,
      boolean needVector,
      boolean needGraph) {
    if (type == QueryType.MEMORY && !needVector && !needGraph) {
      return "MEMORY_RAG";
    }

    if (type == QueryType.SYSTEM_DESIGN
        || complexity == RetrievalComplexity.GLOBAL
        || (needGraph && !needVector && !needMemory)) {
      return "GRAPH_RAG";
    }

    if (type == QueryType.LEARNING && needMemory && !needGraph) {
      return "MEMORY_RAG";
    }

    return "VECTOR_RAG";
  }

  private GraphRagMode chooseGraphMode(
      QueryType type,
      RetrievalComplexity complexity,
      boolean contextDependent) {
    if (complexity == RetrievalComplexity.GLOBAL || type == QueryType.LEARNING) {
      return GraphRagMode.GLOBAL;
    }

    if (type == QueryType.INTERVIEW_FOLLOWUP && contextDependent) {
      return GraphRagMode.DRIFT;
    }

    return GraphRagMode.LOCAL;
  }

  private int chooseTopK(String primary, RetrievalComplexity complexity) {
    if ("MEMORY_RAG".equals(primary)) {
      return 3;
    }

    return switch (complexity) {
      case SIMPLE -> 5;
      case MULTI_HOP -> 6;
      case GLOBAL -> 8;
    };
  }

  private String buildReason(
      PlannerSignal signal,
      QueryType type,
      RetrievalComplexity complexity,
      RetrievalStrategy strategy) {
    String llmReason = safe(signal.reason());
    String summary = "intent=%s, complexity=%s, primary=%s, memory=%s, vector=%s, graph=%s".formatted(
        type.name(),
        complexity.name(),
        strategy.primary(),
        strategy.needMemory(),
        strategy.needVector(),
        strategy.needGraph());

    return llmReason.isBlank() ? summary : llmReason + " | " + summary;
  }

  private String extractJson(String raw) {
    String cleaned = raw.trim();
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

  private String readText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? "" : value.asText("");
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  record PlannerSignal(
      String intent,
      String complexity,
      boolean contextDependent,
      boolean needsHistoricalContext,
      boolean needsConceptRetrieval,
      boolean needsGraphReasoning,
      String reason) {
  }
}
