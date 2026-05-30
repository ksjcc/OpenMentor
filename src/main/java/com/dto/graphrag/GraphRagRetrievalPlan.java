package com.dto.graphrag;

import java.util.List;

import com.common.GraphRagMode;
import com.common.GraphRagRetrieverType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GraphRagRetrievalPlan {
  private GraphRagMode mode;
  private List<GraphRagRetrieverType> retrievers;
  private boolean needRerank;
  private int budget;
  private String reason;
}
