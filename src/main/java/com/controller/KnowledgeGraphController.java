package com.controller;

import com.common.Result;
import com.dto.graph.GraphDTO;
import com.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识图谱接口
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class KnowledgeGraphController {
  private final KnowledgeGraphService knowledgeGraphService;

  /**
   * 获取用户知识图谱
   *
   * GET /api/graph?userId=1
   */
  @GetMapping
  public Result<GraphDTO> graph(
      @RequestParam Long userId) {
    return Result.success(
        knowledgeGraphService.getGraph(userId));
  }
}
