package com.service.admin;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ai.orchestrator.pipeline.TraceContext;
import com.entity.Trace.TraceEvent;
import com.entity.Trace.TraceEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.TraceEventMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracePersistenceService {
  private final TraceEventMapper traceEventMapper;
  private final ObjectMapper objectMapper;

  @Async("traceExecutor")
  public void persist(TraceContext context, Long userId, Long sessionId) {
    if (context == null || context.getEvents() == null) {
      return;
    }
    for (TraceEvent event : context.getEvents()) {
      try {

        TraceEventEntity entity = new TraceEventEntity();

        entity.setTraceId(
            context.getTraceId());

        entity.setUserId(
            userId);

        entity.setSessionId(
            sessionId);

        entity.setStage(
            event.getStage());

        entity.setEventType(
            event.getType() == null || event.getType().isBlank()
                ? event.getStage()
                : event.getType());

        entity.setLatencyMs(
            event.getLatencyMs());

        entity.setMetadataJson(
            objectMapper.writeValueAsString(
                event.getMetadata()));

        traceEventMapper.insert(entity);

      } catch (Exception e) {

        log.error(
            "persist trace failed traceId={}",
            context.getTraceId(),
            e);
      }
    }
  }
}
