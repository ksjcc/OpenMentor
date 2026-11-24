package com.dto.interview;

import lombok.Data;

/**
 * 能力拆项：value 取 0~1，前端直接画进度条。
 */
@Data
public class ReportDimensionDTO {

    private String label;

    private Double value;

    public static ReportDimensionDTO of(String label, Integer score100) {
        ReportDimensionDTO dto = new ReportDimensionDTO();
        dto.setLabel(label);
        dto.setValue(score100 == null ? 0.0 : Math.max(0, Math.min(1, score100 / 100.0)));
        return dto;
    }
}
