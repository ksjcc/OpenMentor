package com.ai.decision;

/**
 * 面试下一步动作
 *
 * FOLLOW_UP：继续追问当前知识点
 * NEXT：进入下一题
 * HARDER：提高难度
 * FINISH：结束面试
 */
public enum DecisionAction {
  FOLLOW_UP,

  NEXT,

  HARDER,

  FINISH
}
