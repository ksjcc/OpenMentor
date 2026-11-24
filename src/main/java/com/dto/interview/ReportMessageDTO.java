package com.dto.interview;

import lombok.Data;

/**
 * 复盘对话中的一条消息。
 */
@Data
public class ReportMessageDTO {

    /** ai / user */
    private String role;

    private String content;

    /** 该轮回答得分，仅 user 消息可能有值。 */
    private Integer score;

    public static ReportMessageDTO of(String role, String content, Integer score) {
        ReportMessageDTO dto = new ReportMessageDTO();
        dto.setRole(role);
        dto.setContent(content);
        dto.setScore(score);
        return dto;
    }
}
