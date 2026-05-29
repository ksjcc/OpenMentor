package com.dto.graphrag;

import com.common.GraphRagMode;
import com.common.GraphRagQueryType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GraphRagQueryPlan {
  private GraphRagQueryType queryType;
  private GraphRagMode mode;
  private boolean needHyde;
  private boolean allowFallback;
  private int maxResults;
  private String reason;
}
