package com.dto.graphrag;

import java.util.List;

import lombok.Data;

@Data
public class GraphExtractionResult {
  private List<GraphEntityDTO> entities;
  private List<GraphRelationDTO> relationships;
}
