package com.dto.graphrag;

import com.common.GraphRagMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalPlanDTO {
  private boolean needMemory;

  private boolean needGraph;

  private boolean needHyde;

  private GraphRagMode graphMode;

  private Integer topK;

  private String query;

  private String reason;
}
