package com.dto.graphrag;

import lombok.Data;

@Data
public class GraphRelationDTO {
  private String source;
  private String target;
  private String type;
  private String description;
  private Double weight;
}
