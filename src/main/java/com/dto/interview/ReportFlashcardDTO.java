package com.dto.interview;

import lombok.Data;

/**
 * 复盘页的闪卡：题干 + 答案 + 标签。
 */
@Data
public class ReportFlashcardDTO {

    private String q;

    private String a;

    private String tag;

    public static ReportFlashcardDTO of(String question, String answer, String tag) {
        ReportFlashcardDTO dto = new ReportFlashcardDTO();
        dto.setQ(question);
        dto.setA(answer);
        dto.setTag(tag);
        return dto;
    }
}
