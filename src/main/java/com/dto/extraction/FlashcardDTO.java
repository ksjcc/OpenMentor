package com.dto.extraction;

import lombok.Data;

@Data
public class FlashcardDTO {
  /**
   * 问题（闪卡正面）
   */
  private String question;

  /**
   * 答案（闪卡背面）
   */
  private String answer;

  /**
   * 难度（1~5）
   */
  private Integer difficulty;
}
