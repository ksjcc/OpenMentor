package com.entity.Trace;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("trace_event")
public class TraceEventEntity {
  private Long id;

  private String traceId;

  private Long sessionId;

  private Long userId;

  private String stage;

  private String eventType;

  private Long latencyMs;

  private String metadataJson;

  private LocalDateTime createdAt;
}
