package com.entity;

public enum ProfileMergeMode {

  // 初始化后不自动改
  INIT_ONLY,

  // 明确 patch 才允许覆盖
  EXPLICIT_OVERRIDE,

  // 集合去重合并
  UNION_BY_NAME,

  // 能力分数平滑更新
  CONFIDENCE_WEIGHTED_SCORE,

  // 证据追加
  APPEND_EVIDENCE
}
