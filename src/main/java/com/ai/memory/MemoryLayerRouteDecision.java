package com.ai.memory;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryLayerRouteDecision {
  private MemoryLayer primaryLayer;
  private MemoryLayer fallbackLayer;
  private List<String> selectedStorage;
  private List<String> fallbackStorage;
  private String reason;
}
