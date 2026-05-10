package com.dto.extraction;

import lombok.Data;

import java.util.List;

/**
 * KnowledgeExtractorAgent 返回结果
 */
@Data
public class KnowledgeExtractionResult {
  /**
   * 本次面试总结
   */
  private String summary;

  /**
   * 用户优势
   */
  private List<StrengthDTO> strengths;

  /**
   * 用户薄弱点
   */
  private List<WeaknessDTO> weaknesses;

  /**
   * 生成的闪卡
   */
  private List<FlashcardDTO> flashcards;

  /**
   * 长期记忆相关内容
   */
  private MemoryDTO memory;

  /**
   * 下一步训练建议
   */
  private List<String> nextPlan;
}
