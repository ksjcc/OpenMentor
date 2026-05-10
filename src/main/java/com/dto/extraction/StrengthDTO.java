package com.dto.extraction;

import lombok.Data;

@Data
public class StrengthDTO {
  /**
   * 优势名称
   */
  private String name;

  /**
   * AI提炼依据
   */
  private String evidence;
}
