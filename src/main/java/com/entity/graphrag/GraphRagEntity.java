package com.entity.graphrag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_entity")
public class GraphRagEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 实体名称，例如 Redis、缓存一致性、延迟双删
     */
    private String name;

    /**
     * 实体类型：TECH / PROJECT / WEAKNESS / SOLUTION / CONCEPT
     */
    private String type;

    /**
     * 实体描述
     */
    private String description;

    /**
     * 来源文本单元ID
     */
    private Long textUnitId;

    /**
     * 出现次数
     */
    private Integer occurrenceCount;

    /**
     * 实体重要度 0~100
     */
    private Integer importanceScore;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
