package com.ai.runtime;

import java.util.List;

import com.ai.prompt.PromptActionType;
import com.ai.prompt.PromptContext;

/**
 * 线程级 Runtime 埋点容器。
 *
 * <p>当前实现刻意保持轻量：
 * 1. 不改动现有主业务接口；
 * 2. 通过 ThreadLocal 汇聚一次编排请求内的 Prompt/LLM/Fallback 指标；
 * 3. 既可写入 trace，也可挂到返回对象中用于联调和 benchmark。
 */
public final class PromptRuntimeTracker {
  private static final ThreadLocal<PromptRuntimeMetrics> METRICS = new ThreadLocal<>();

  private PromptRuntimeTracker() {
  }

  public static void beginRequest() {
    METRICS.set(PromptRuntimeMetrics.builder().build());
  }

  public static void clear() {
    METRICS.remove();
  }

  public static PromptRuntimeMetrics snapshot() {
    PromptRuntimeMetrics current = METRICS.get();
    if (current == null) {
      return PromptRuntimeMetrics.builder().build();
    }
    return current.toBuilder().build();
  }

  public static void recordCompression(PromptContext raw, PromptContext compressed, PromptActionType action) {
    PromptRuntimeMetrics metrics = current();
    long rawChars = contextChars(raw);
    long compressedChars = contextChars(compressed);
    long rawTokens = contextTokens(raw);
    long compressedTokens = contextTokens(compressed);
    long saved = Math.max(0L, rawTokens - compressedTokens);

    metrics.setRawContextChars(rawChars);
    metrics.setCompressedContextChars(compressedChars);
    metrics.setEstimatedRawContextTokens(rawTokens);
    metrics.setEstimatedCompressedContextTokens(compressedTokens);
    metrics.setEstimatedContextTokensSaved(saved);
    metrics.setCompressionRatio(rawTokens <= 0 ? 1.0 : ((double) compressedTokens) / rawTokens);
    metrics.setPromptBudgetApplied(compressedTokens < rawTokens || compressedChars < rawChars);
    metrics.setPromptAction(action == null ? "" : action.name());
  }

  public static void recordLlmCall(
      String prompt,
      String response,
      int attempts,
      boolean modelSuccess,
      boolean fallbackUsed) {
    PromptRuntimeMetrics metrics = current();
    long inputTokens = PromptRuntimeTokenEstimator.estimate(prompt);
    long outputTokens = modelSuccess ? PromptRuntimeTokenEstimator.estimate(response) : 0L;

    metrics.setLlmCallCount(metrics.getLlmCallCount() + 1);
    metrics.setLlmRetryCount(metrics.getLlmRetryCount() + Math.max(0, attempts - 1));
    metrics.setEstimatedInputTokens(metrics.getEstimatedInputTokens() + inputTokens);
    metrics.setEstimatedOutputTokens(metrics.getEstimatedOutputTokens() + outputTokens);
    metrics.setEstimatedTotalTokens(metrics.getEstimatedInputTokens() + metrics.getEstimatedOutputTokens());

    if (modelSuccess) {
      metrics.setLlmSuccessCount(metrics.getLlmSuccessCount() + 1);
    }
    if (fallbackUsed) {
      metrics.setLlmFallbackCount(metrics.getLlmFallbackCount() + 1);
      metrics.setFallbackTriggered(true);
      metrics.setFallbackStrategy("LLM_SAFE_CHAT_FALLBACK");
      metrics.setFallbackSuccess(response != null && !response.isBlank());
    }
  }

  public static void recordGeneration(String prompt, String response) {
    PromptRuntimeMetrics metrics = current();
    metrics.setEstimatedGenerationInputTokens(PromptRuntimeTokenEstimator.estimate(prompt));
    metrics.setEstimatedGenerationOutputTokens(PromptRuntimeTokenEstimator.estimate(response));
  }

  public static void recordFallback(String strategy, boolean success) {
    PromptRuntimeMetrics metrics = current();
    metrics.setFallbackTriggered(true);
    metrics.setFallbackStrategy(strategy);
    metrics.setFallbackSuccess(success);
  }

  public static void recordGuardTriggered() {
    current().setGuardTriggered(true);
  }

  public static void markRequestCompleted(boolean requestSuccess, boolean finalResponseAvailable) {
    PromptRuntimeMetrics metrics = current();
    metrics.setRequestSuccess(requestSuccess);
    metrics.setFinalResponseAvailable(finalResponseAvailable);
  }

  private static PromptRuntimeMetrics current() {
    PromptRuntimeMetrics metrics = METRICS.get();
    if (metrics == null) {
      metrics = PromptRuntimeMetrics.builder().build();
      METRICS.set(metrics);
    }
    return metrics;
  }

  private static long contextChars(PromptContext context) {
    if (context == null) {
      return 0L;
    }
    return joinContext(context).length();
  }

  private static long contextTokens(PromptContext context) {
    return PromptRuntimeTokenEstimator.estimate(joinContext(context));
  }

  private static String joinContext(PromptContext context) {
    if (context == null) {
      return "";
    }
    return String.join("\n",
        safe(context.getProfileJson()),
        safe(context.getSummaryMd()),
        joinList(context.getRagMemories()),
        safe(context.getRecentContext()),
        safe(context.getCurrentQuestion()),
        safe(context.getCurrentAnswer()),
        safe(context.getScoreJson()),
        safe(context.getTopic()),
        safe(context.getTargetRole()),
        safe(context.getResumeText()),
        safe(context.getProjectSummary()),
        safe(context.getGraphReasoning()));
  }

  private static String joinList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return String.join("\n", values.stream().map(PromptRuntimeTracker::safe).toList());
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
