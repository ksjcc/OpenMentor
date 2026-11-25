package com.dto.interview;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 一场面试的复盘报告。
 *
 * <p>
 * 全部字段都来自真实数据：session 行 + interview_message + 结束时的知识提炼 JSON。
 * 没有任何"演示兜底"。
 */
@Data
public class InterviewReportDTO {

    private String sessionId;

    private String topic;

    private String targetRole;

    private String interviewType;

    private String status;

    /** 本场加权总分（各轮得分的平均）；未评分时为 null。 */
    private Integer score;

    private Integer turns;

    private Long durationSec;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    /** 本场注入的简历名称，便于回看"这场是拿哪份简历面的"。 */
    private String resumeTitle;

    /** 本场是否已下沉到长期记忆（画像 / 记忆片段 / 复习卡 / 图谱）。 */
    private Boolean remembered;

    private String summary;

    private List<String> strengths = new ArrayList<>();

    private List<String> weaknesses = new ArrayList<>();

    private List<ReportDimensionDTO> dimensions = new ArrayList<>();

    private List<ReportFlashcardDTO> flashcards = new ArrayList<>();

    private List<ReportMessageDTO> messages = new ArrayList<>();

    private List<String> nextPlan = new ArrayList<>();
}
