package com.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.common.BizException;
import com.common.ErrorCode;
import com.common.Result;
import com.dto.resume.ResumeSaveRequest;
import com.entity.Resume;
import com.service.resume.ResumeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 简历库接口。
 *
 * <p>
 * 支持两种录入方式：直接粘贴文本（JSON）与上传文件（pdf/docx/txt）。
 * 上传成功后会异步调用模型解析成结构化画像（parse=true 时）。
 */
@RestController
@RequestMapping("/api/resume")
@Tag(name = "简历库", description = "用户个人简历库与简历解析")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping
    @Operation(summary = "简历列表")
    public Result<List<Resume>> list(@RequestParam Long userId) {
        return Result.success(resumeService.listForDisplay(userId));
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "简历详情")
    public Result<Resume> get(@RequestParam Long userId, @PathVariable Long resumeId) {
        return Result.success(resumeService.get(userId, resumeId));
    }

    @PostMapping
    @Operation(summary = "粘贴文本新建简历")
    public Result<Resume> create(@RequestBody ResumeSaveRequest request) {
        boolean parse = Boolean.TRUE.equals(request.getParse());
        return Result.success(resumeService.createFromText(
                request.getUserId(),
                request.getTitle(),
                request.getContent(),
                request.getTargetRole(),
                parse));
    }

    @PutMapping("/{resumeId}")
    @Operation(summary = "更新简历")
    public Result<Resume> update(@PathVariable Long resumeId, @RequestBody ResumeSaveRequest request) {
        return Result.success(resumeService.update(
                request.getUserId(),
                resumeId,
                request.getTitle(),
                request.getContent(),
                request.getTargetRole()));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传简历文件（PDF / DOCX / TXT）")
    public Result<Resume> upload(
            @RequestParam Long userId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false, defaultValue = "true") boolean parse,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择一个简历文件");
        }
        try {
            return Result.success(resumeService.createFromFile(
                    userId,
                    title,
                    file.getOriginalFilename(),
                    file.getBytes(),
                    targetRole,
                    parse));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取上传文件失败：" + ex.getMessage());
        }
    }

    @PostMapping("/{resumeId}/parse")
    @Operation(summary = "重新解析简历为结构化画像")
    public Result<Resume> parse(@RequestParam Long userId, @PathVariable Long resumeId) {
        return Result.success(resumeService.parse(userId, resumeId));
    }

    @PostMapping("/{resumeId}/default")
    @Operation(summary = "设为默认简历")
    public Result<Resume> setDefault(@RequestParam Long userId, @PathVariable Long resumeId) {
        return Result.success(resumeService.setDefault(userId, resumeId));
    }

    @DeleteMapping("/{resumeId}")
    @Operation(summary = "删除简历")
    public Result<String> delete(@RequestParam Long userId, @PathVariable Long resumeId) {
        resumeService.delete(userId, resumeId);
        return Result.success("deleted");
    }
}
