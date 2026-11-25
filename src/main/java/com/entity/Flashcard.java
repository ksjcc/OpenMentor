package com.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 闪卡（用于复习系统）
 */
@Data
@TableName("flashcard")
public class Flashcard {
    private Long id;

    private Long userId;

    /**
     * 问题（正面）
     */
    private String question;

    /**
     * 答案（背面）
     */
    private String answer;

    /**
     * 知识点主题（例如「Redis 持久化」），用于按主题聚合与「关联知识点」弹窗。
     */
    private String topic;

    /**
     * 关联知识点ID（可选）
     */
    private Long knowledgeNodeId;

    /**
     * 难度（1-5）
     */
    private Integer difficulty;

    /**
     * 掌握程度（0-100）
     */
    private Integer masteryLevel;

    /**
     * 下一次复习时间
     */
    private LocalDateTime nextReviewTime;

    private LocalDateTime createdAt;
}
