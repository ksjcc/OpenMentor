package com.controller;

import com.common.Result;
import com.dto.dashboard.DashboardDTO;
import com.service.Dashboard.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "仪表盘", description = "首页仪表盘相关接口")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取首页 Dashboard 数据
     */
    @GetMapping
    public Result<DashboardDTO> dashboard(
            @RequestParam Long userId) {
        return Result.success(
                dashboardService.getDashboard(userId));
    }
}
