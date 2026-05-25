package com.dto.graphrag;

import com.common.GraphRagMode;
import com.common.GraphRagQueryType;

import lombok.Data;

@Data
public class GraphRagQueryPlan {
  private GraphRagQueryType queryType;
  private GraphRagMode mode;
  private String reason;
}
