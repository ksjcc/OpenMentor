package com.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dto.extraction.StrengthDTO;

import lombok.Data;

@Data
@TableName("memory_profile_json")
public class MemoryProfileJson {
    private Long id;
    private Long userId;
    private String profileData;
    private Integer version;
}
