package com.service.garphrag;

import java.util.List;

import org.springframework.stereotype.Service;

import com.entity.graphrag.GraphCommunityReport;
import com.mapper.GraphCommunityReportMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class GraphRagGlobalSearchService {
  private final GraphCommunityReportMapper reportMapper;

  /**
   * Global Search：
   * 使用社区报告作为全局上下文。
   */
  public List<String> search(Long userId, String query) {
    List<GraphCommunityReport> reports = reportMapper.selectTopReports(userId, 5);
    return reports.stream()
        .map(r -> """
            【社区报告】
            标题：%s
            摘要：%s
            发现：%s
            """.formatted(
            r.getTitle(),
            r.getSummary(),
            r.getFindingsJson()))
        .toList();
  }

}
