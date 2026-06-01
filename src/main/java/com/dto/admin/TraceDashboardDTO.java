package com.dto.admin;

import java.util.Map;

import com.mapper.TraceEventMapper;

import lombok.Data;

@Data
public class TraceDashboardDTO {

  private Double avgLatencyMs;

  private Double retrievalLatencyMs;

  private Double generationLatencyMs;

  private Integer traceCount;

  private Integer graphUsageCount;

  private Integer fallbackCount;

  private Double fallbackRate;

  private Map<String, Integer> stageUsage;
}
