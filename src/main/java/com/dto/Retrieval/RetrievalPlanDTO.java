package com.dto.Retrieval;

import java.util.List;

import com.ai.memory.MemoryLayer;
import com.common.GraphRagMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalPlanDTO {
  private String primaryRetriever;

  private boolean needMemory;

  private boolean needVector;

  private boolean needGraph;

  private boolean needHyde;

  private boolean enableKeywordFallback;

  private GraphRagMode graphMode;

  private Integer topK;

  private String query;

  private String reason;

  private QueryType queryType;

  private RetrievalComplexity complexity;

  private boolean contextDependent;

  private int retrievalBudget;

  private MemoryLayer memoryLayer;

  private MemoryLayer fallbackMemoryLayer;

  private List<String> selectedStorage;

  private List<String> fallbackStorage;

}
