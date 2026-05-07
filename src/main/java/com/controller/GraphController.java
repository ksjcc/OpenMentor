package com.controller;

import com.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/graph")
@Tag(name = "能力图谱", description = "能力图谱相关接口")
public class GraphController {

    @GetMapping("/status")
    @Operation(summary = "能力图谱模块状态")
    public Result<Map<String, String>> status() {
        return Result.success(Map.of("module", "graph", "status", "ready"));
    }
}
