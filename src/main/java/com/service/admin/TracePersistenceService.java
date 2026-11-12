package com.service.admin;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.entity.Trace.TraceEvent;
import com.entity.Trace.TraceEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.TraceEventMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 轨迹落库。
 *
 * <p>改造后不再依赖流水线内部的 TraceContext：编排由 HarnessAgent 负责，
 * 需要落库时直接把 traceId + 事件列表传进来。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TracePersistenceService {
  private final TraceEventMapper traceEventMapper;
  private final ObjectMapper objectMapper;

  @Async("traceExecutor")
  public void persist(String traceId, List<TraceEvent> events, Long userId, Long sessionId) {
    if (events == null || events.isEmpty()) {
      return;
    }
    for (TraceEvent event : events) {
      try {
        TraceEventEntity entity = new TraceEventEntity();
        entity.setTraceId(traceId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setStage(event.getStage());
        entity.setEventType(
            event.getType() == null || event.getType().isBlank()
                ? event.getStage()
                : event.getType());
        entity.setLatencyMs(event.getLatencyMs());
        entity.setMetadataJson(objectMapper.writeValueAsString(event.getMetadata()));
        traceEventMapper.insert(entity);
      } catch (Exception e) {
        log.error("persist trace failed traceId={}", traceId, e);
      }
    }
  }
}
