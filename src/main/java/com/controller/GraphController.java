package com.controller;

import com.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    @GetMapping("/status")
    public Result<Map<String, String>> status() {
        return Result.success(Map.of("module", "graph", "status", "ready"));
    }
}
