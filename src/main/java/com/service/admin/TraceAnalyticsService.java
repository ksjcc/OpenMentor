package com.service.admin;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.admin.TraceDashboardDTO;
import com.entity.Trace.TraceEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.TraceEventMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TraceAnalyticsService {
  private final TraceEventMapper traceEventMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TraceDashboardDTO analyzeRecent(int days) {
    LocalDateTime start = LocalDateTime.now().minusDays(days);
    List<TraceEventEntity> events = traceEventMapper
        .selectList(new LambdaQueryWrapper<TraceEventEntity>().gt(TraceEventEntity::getCreatedAt, start));
    if (events == null || events.isEmpty()) {
      TraceDashboardDTO empty = new TraceDashboardDTO();
      empty.setTraceCount(0);
      empty.setAvgLatencyMs(0.0);
      empty.setRetrievalLatencyMs(0.0);
      empty.setGenerationLatencyMs(0.0);
      empty.setFallbackRate(0.0);
      empty.setStageUsage(
          Collections.emptyMap());
      return empty;
    }
    int traceCount = events.size();
    // 平均响应
    double avgLatency = events.stream()
        .filter(e -> e.getLatencyMs() != null)
        .mapToLong(
            TraceEventEntity::getLatencyMs)
        .average()
        .orElse(0);
    // Rag检索耗时
    double retrievalLatency = events.stream()
        .filter(e -> "hybrid_retrieval"
            .equals(resolveEventType(e)))
        .filter(e -> e.getLatencyMs() != null)
        .mapToLong(
            TraceEventEntity::getLatencyMs)
        .average()
        .orElse(0);
    // llm生成耗时
    double generationLatency = events.stream()
        .filter(e -> "generation"
            .equals(resolveEventType(e)))
        .filter(e -> e.getLatencyMs() != null)
        .mapToLong(
            TraceEventEntity::getLatencyMs)
        .average()
        .orElse(0);
    // 使用频率
    Map<String, Integer> stageUsage = new HashMap<>();

    for (TraceEventEntity event : events) {

      String stage = event.getStage();

      if (stage == null) {
        continue;
      }

      stageUsage.merge(
          stage,
          1,
          Integer::sum);
    }
    // GraphRAG的使用次数
    int graphUsage = 0;
    int fallbackCount = 0;

    for (TraceEventEntity event : events) {

      try {

        if (event.getMetadataJson() == null) {
          continue;
        }

        JsonNode metadata = objectMapper.readTree(
            event.getMetadataJson());

        /*
         * usedMode
         */
        JsonNode modeNode = metadata.get("usedMode");

        if (modeNode != null
            && !"LOCAL"
                .equalsIgnoreCase(
                    modeNode.asText())) {

          graphUsage++;
        }

        /*
         * fallback
         */
        JsonNode fallbackNode = metadata.get(
            "fallbackTriggered");

        if (fallbackNode != null
            && fallbackNode.asBoolean()) {

          fallbackCount++;
        }

      } catch (Exception ignore) {
      }
    }

    double fallbackRate = traceCount == 0
        ? 0
        : (double) fallbackCount
            / traceCount;

    TraceDashboardDTO dto = new TraceDashboardDTO();

    dto.setTraceCount(traceCount);
    dto.setAvgLatencyMs(avgLatency);
    dto.setRetrievalLatencyMs(
        retrievalLatency);
    dto.setGenerationLatencyMs(
        generationLatency);
    dto.setGraphUsageCount(
        graphUsage);
    dto.setFallbackCount(
        fallbackCount);
    dto.setFallbackRate(
        fallbackRate);
    dto.setStageUsage(
        stageUsage);

    return dto;
  }

  private String resolveEventType(TraceEventEntity event) {
    if (event == null) {
      return "";
    }
    if (event.getEventType() != null && !event.getEventType().isBlank()) {
      return event.getEventType();
    }
    return event.getStage() == null ? "" : event.getStage();
  }
}
