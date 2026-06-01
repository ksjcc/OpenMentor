package com.entity.Trace;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceEvent {
  private String stage;

  private long startTime;

  private long endTime;

  private long latencyMs;

  private String input;

  private String output;
  private String Type;

  private Map<String, Object> metadata;

  public String getType() {
    return Type;
  }

  public void setType(String type) {
    this.Type = type;
  }
}
