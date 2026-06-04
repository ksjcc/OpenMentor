package com.ai.runtime;

import lombok.Builder;
import lombok.Data;

/**
 * Prompt Runtime 轻量埋点结果。
 *
 * <p>说明：
 * 1. 当前项目里的 token 统计为估算值，不是模型 API 原生 usage。
 * 2. 估算规则由 {@link PromptRuntimeTokenEstimator} 提供，适合离线 benchmark 和趋势对比。
 * 3. 如果后续接入模型原生 usage 字段，可直接替换这些估算值。
 */
@Data
@Builder(toBuilder = true)
public class PromptRuntimeMetrics {
  @Builder.Default
  private String tokenEstimationMethod = "heuristic_v1_chars_and_terms";

  private long rawContextChars;
  private long compressedContextChars;
  private long estimatedRawContextTokens;
  private long estimatedCompressedContextTokens;
  private long estimatedContextTokensSaved;
  private double compressionRatio;

  private boolean promptBudgetApplied;
  private String promptAction;

  private int llmCallCount;
  private int llmSuccessCount;
  private int llmFallbackCount;
  private int llmRetryCount;

  /**
   * 整个请求链路内所有 LLM 调用的估算输入 token 总和。
   */
  private long estimatedInputTokens;

  /**
   * 整个请求链路内所有 LLM 调用的估算输出 token 总和。
   */
  private long estimatedOutputTokens;

  private long estimatedTotalTokens;

  /**
   * 最终 generation 阶段的 prompt/response 估算 token，便于单独比较最终回答生成成本。
   */
  private long estimatedGenerationInputTokens;
  private long estimatedGenerationOutputTokens;

  private boolean guardTriggered;
  private boolean fallbackTriggered;
  private String fallbackStrategy;
  private boolean fallbackSuccess;
  private boolean requestSuccess;
  private boolean finalResponseAvailable;
}
