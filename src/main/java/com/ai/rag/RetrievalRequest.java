package com.ai.rag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.ai.memory.MemoryLayer;
import com.common.GraphRagMode;
import com.dto.Retrieval.QueryType;

/**
 * 一次检索请求：模型可表达的检索意图。
 *
 * <p>刻意用一套"模型友好"的词汇（范围 / 深度 / 记忆层），而不是直接把
 * {@code RetrievalPlanDTO} 的 20 个字段暴露给模型 —— 字段太多，模型只会瞎填。
 * 由 {@link RetrievalPlanFactory} 负责翻译成规范化的检索计划。
 *
 * <p>三个维度：
 * <ul>
 *   <li>{@link Scope}：查哪几类数据源（记忆 / 向量 / 图谱）</li>
 *   <li>{@link Depth}：查多深（决定条数、HyDE、预算）</li>
 *   <li>{@link MemoryLayer}：查记忆时落在哪一层（本场 / 成长 / 偏好）</li>
 * </ul>
 */
public record RetrievalRequest(
    String query,
    Set<Scope> scopes,
    MemoryLayer memoryLayer,
    GraphRagMode graphMode,
    Depth depth,
    Integer topK,
    Boolean useHyde,
    QueryType queryType,
    boolean contextDependent) {

  /** 数据源。顺序有意义：第一个作为主检索器。 */
  public enum Scope {
    /** 该用户的历史面试、长期记忆、画像 */
    MEMORY,
    /** 通用技术知识库（向量召回） */
    VECTOR,
    /** 知识点之间的关系与多跳推理 */
    GRAPH;

    public static Scope parse(String raw) {
      if (raw == null || raw.isBlank()) {
        throw new IllegalArgumentException("scope 不能为空");
      }
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      for (Scope scope : values()) {
        if (scope.name().equals(normalized)) {
          return scope;
        }
      }
      throw new IllegalArgumentException(
          "未知的 scope: " + raw + "。只能取 MEMORY / VECTOR / GRAPH");
    }
  }

  /** 检索深度。同时决定条数、查询扩展与预算，避免模型为了"多查"而无限调用。 */
  public enum Depth {
    /** 快速确认：2 条，不做查询扩展 */
    LIGHT,
    /** 常规：4 条 */
    NORMAL,
    /** 全面：6 条 + 查询扩展 + 更大预算 */
    DEEP;

    public static Depth parse(String raw, Depth fallback) {
      if (raw == null || raw.isBlank()) {
        return fallback;
      }
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      for (Depth depth : values()) {
        if (depth.name().equals(normalized)) {
          return depth;
        }
      }
      throw new IllegalArgumentException(
          "未知的 depth: " + raw + "。只能取 LIGHT / NORMAL / DEEP");
    }
  }

  public static MemoryLayer parseLayer(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    for (MemoryLayer layer : MemoryLayer.values()) {
      if (layer.name().equals(normalized)) {
        return layer;
      }
    }
    throw new IllegalArgumentException(
        "未知的 memoryLayer: " + raw + "。只能取 SESSION / GROWTH / PREFERENCE");
  }

  public static GraphRagMode parseGraphMode(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    for (GraphRagMode mode : GraphRagMode.values()) {
      if (mode.name().equals(normalized)) {
        return mode;
      }
    }
    throw new IllegalArgumentException(
        "未知的 graphMode: " + raw + "。只能取 LOCAL / GLOBAL / DRIFT / AUTO");
  }

  public static QueryType parseQueryType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    for (QueryType type : QueryType.values()) {
      if (type.name().equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 queryType: " + raw);
  }

  /** 把模型给的 scopes 数组解析成有序集合（去重、保序）。 */
  public static Set<Scope> parseScopes(Iterable<?> raw) {
    Set<Scope> scopes = new LinkedHashSet<>();
    if (raw != null) {
      for (Object item : raw) {
        scopes.add(Scope.parse(item == null ? null : String.valueOf(item)));
      }
    }
    return scopes;
  }

  /** 主检索器：取模型给出的第一个 scope。 */
  public Scope primaryScope() {
    return scopes.isEmpty() ? Scope.VECTOR : scopes.iterator().next();
  }

  public boolean needs(Scope scope) {
    return scopes.contains(scope);
  }
}
