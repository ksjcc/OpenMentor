package com.ai.orchestrator.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.entity.Trace.TraceEvent;

import lombok.Data;

@Data
public class TraceContext {
  private String traceId;
  private List<TraceEvent> events = new ArrayList<>();

  public void addEvent(TraceEvent event) {
    events.add(event);
  }
}
