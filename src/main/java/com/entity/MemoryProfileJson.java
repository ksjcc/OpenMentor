package com.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("memory_profile_json")
public class MemoryProfileJson {
    private Long id;
    private Long userId;
    private String profileData;
    private Integer version;
}
