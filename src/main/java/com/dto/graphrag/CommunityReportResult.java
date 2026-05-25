package com.dto.graphrag;

import lombok.Data;

import java.util.List;

@Data
public class CommunityReportResult {
  private String title;
  private String summary;
  private List<CommunityFindingDTO> findings;
  private Double rankScore;
}
