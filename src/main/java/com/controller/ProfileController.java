package com.controller;

import com.common.Result;
import com.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "用户画像", description = "用户画像相关接口")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/status")
    @Operation(summary = "用户画像模块状态")
    public Result<Map<String, String>> status() {
        return Result.success(profileService.status());
    }
}
