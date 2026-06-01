package com.service;

import com.ai.guard.GuardException;
import com.ai.guard.GuardService;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.graphrag.CommunityReportResult;
import com.dto.graphrag.GraphExtractionResult;
import com.dto.dashboard.LearningPlanDTO;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.profile.ProfileUpdatePatchDTO;
import com.dto.score.ScoreResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
  private final GuardService guardService;

  /**
   * 解析 KnowledgeExtractorAgent 返回结果
   *
   * @param json AI返回的JSON字符串
   * @return Java对象
   */
  public KnowledgeExtractionResult parseExtraction(String json) {

    try {

      return guardService.validate(
          normalizeJson(json),
          KnowledgeExtractionResult.class);

    } catch (Exception e) {

      throw new BizException(
          ErrorCode.INTERNAL_ERROR,
          "解析知识提炼JSON失败：" + e.getMessage());
    }
  }

  public GraphExtractionResult parseGraphExtraction(String json) {
    if (json == null || json.isBlank()) {
      return new GraphExtractionResult();
    }

    try {
      return guardService.validate(
          normalizeJson(json),
          GraphExtractionResult.class);
    } catch (Exception e) {
      throw new BizException(
          ErrorCode.INTERNAL_ERROR,
          "解析 GraphRAG 抽取 JSON 失败：" + e.getMessage());
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

  public ScoreResultDTO parseScore(String json) {
    try {
      String cleanJson = cleanJson(json);
      return guardService.validate(cleanJson, ScoreResultDTO.class);
    } catch (Exception e) {
      ScoreResultDTO fallback = new ScoreResultDTO();
      fallback.setScore(50);
      fallback.setTechnical(15);
      fallback.setCompleteness(15);
      fallback.setExpression(20);
      fallback.setIsWeak(true);
      fallback.setFeedback("评分解析失败，使用默认低分兜底。");
      fallback.setWeakPoints(List.of("回答不完整"));
      return fallback;
    }
  }

  public CommunityReportResult parseCommunityReport(String json) {
    if (json == null || json.isBlank()) {
      return new CommunityReportResult();
    }

    try {
      String normalized = normalizeJson(json);
      return guardService.validate(normalized, CommunityReportResult.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("解析 GraphRAG 社区报告 JSON 失败：" + e.getMessage(), e);
    }
  }

  public KnowledgeExtractionResult parseExtractionLenient(String json) {
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

  public GraphExtractionResult parseGraphExtractionLenient(String json) {
    if (json == null || json.isBlank()) {
      return new GraphExtractionResult();
    }

    try {
      return objectMapper.readValue(
          normalizeJson(json),
          GraphExtractionResult.class);
    } catch (Exception e) {
      throw new BizException(
          ErrorCode.INTERNAL_ERROR,
          "解析 GraphRAG 抽取 JSON 失败：" + e.getMessage());
    }
  }

  public CommunityReportResult parseCommunityReportLenient(String json) {
    if (json == null || json.isBlank()) {
      return new CommunityReportResult();
    }

    try {
      return objectMapper.readValue(
          normalizeJson(json),
          CommunityReportResult.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("解析 GraphRAG 社区报告 JSON 失败：" + e.getMessage(), e);
    }
  }

  public ScoreResultDTO parseScoreLenient(String json) {
    try {
      return objectMapper.readValue(
          cleanJson(json),
          ScoreResultDTO.class);
    } catch (Exception e) {
      throw new GuardException("score_parse_failed", e);
    }
  }

  public ProfileUpdatePatchDTO parseProfileUpdatePatch(String json) {
    try {
      return guardService.validate(
          normalizeJson(json),
          ProfileUpdatePatchDTO.class);
    } catch (Exception e) {
      throw new BizException(
          ErrorCode.INTERNAL_ERROR,
          "解析画像更新 Patch JSON 失败：" + e.getMessage());
    }
  }

  public LearningPlanDTO parseLearningPlan(String json) {
    try {
      return guardService.validate(
          normalizeJson(json),
          LearningPlanDTO.class);
    } catch (Exception e) {
      throw new BizException(
          ErrorCode.INTERNAL_ERROR,
          "解析学习计划 JSON 失败：" + e.getMessage());
    }
  }

  public List<String> parseDriftQuestion(String json) {
    try {
      JsonNode root = objectMapper.readTree(cleanJson(json));
      JsonNode queries = root.path("queries");
      if (!queries.isArray()) {
        return List.of();
      }

      List<String> result = new ArrayList<>();
      for (JsonNode query : queries) {
        String value = query.asText();
        if (value != null && !value.isBlank()) {
          result.add(value);
        }
      }
      return result;
    } catch (Exception e) {
      return List.of();
    }
  }

  /**
   * 清洗 AI 返回的 JSON
   */
  private String cleanJson(String raw) {
    if (raw == null) {
      return "{}";
    }

    String text = raw.trim();

    text = text.replace("```json", "")
        .replace("```", "")
        .trim();

    int start = text.indexOf("{");
    int end = text.lastIndexOf("}");

    if (start >= 0 && end > start) {
      return text.substring(start, end + 1);
    }

    return text;
  }
}
