package com.dto.interview;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 面试列表项（我的面试记录）。
 */
@Data
public class InterviewSummaryDTO {

    private Long sessionId;

    private String topic;

    private String targetRole;

    private String interviewType;

    private String status;

    private Integer score;

    /** AI 提问轮次。 */
    private Integer turns;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    /** 本场最突出的一条薄弱点，用于列表里给个直观提示。 */
    private String topWeakness;
}
