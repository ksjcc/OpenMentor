package com.ai.runtime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 token 估算器。
 *
 * <p>解析规则：
 * 1. 中文按单字近似计数；
 * 2. 英文/数字连续片段按每 4 个字符约 1 token 估算；
 * 3. 其他可见符号按每 2 个字符约 1 token 估算。
 *
 * <p>这是工程估算口径，目标是支撑 baseline/runtime 的相对比较，而不是复刻模型底层 tokenizer。
 */
public final class PromptRuntimeTokenEstimator {
  private static final Pattern TERM_PATTERN = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9_]+|[^\\s]");

  private PromptRuntimeTokenEstimator() {
  }

  public static long estimate(String text) {
    if (text == null || text.isBlank()) {
      return 0L;
    }

    long estimated = 0L;
    Matcher matcher = TERM_PATTERN.matcher(text);
    while (matcher.find()) {
      String term = matcher.group();
      if (term == null || term.isBlank()) {
        continue;
      }
      if (term.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
        estimated += term.length();
      } else if (term.matches("[A-Za-z0-9_]+")) {
        estimated += Math.max(1, (long) Math.ceil(term.length() / 4.0));
      } else {
        estimated += Math.max(1, (long) Math.ceil(term.length() / 2.0));
      }
    }
    return estimated;
  }
}
