package com.service;

import com.common.BizException;
import com.common.ErrorCode;
import com.dto.extraction.KnowledgeExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * JSON解析服务
 *
 * 负责：
 * 将AI返回的JSON字符串转换成Java对象
 */
@Service
@RequiredArgsConstructor
public class JsonParseService {
  /**
   * Jackson JSON解析器
   */
  private final ObjectMapper objectMapper;

  /**
   * 解析 KnowledgeExtractorAgent 返回结果
   *
   * @param json AI返回的JSON字符串
   * @return Java对象
   */
  public KnowledgeExtractionResult parseExtraction(String json) {

    try {

      return objectMapper.readValue(
          normalizeJson(json),
          KnowledgeExtractionResult.class);

    } catch (Exception e) {

      throw new BizException(
          ErrorCode.INTERNAL_ERROR,
          "解析知识提炼JSON失败：" + e.getMessage());
    }
  }

  private String normalizeJson(String json) {
    if (json == null || json.isBlank()) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "知识提炼结果为空");
    }

    String cleaned = json.trim();
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
}
