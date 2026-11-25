package com.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 一次闪卡作答记录。
 *
 * <p>
 * 用来算「每天的正确率 / 错误次数」，也是「关联知识点」弹窗里"这道题历史上做过几次、答没答对"的来源。
 */
@Data
@TableName("review_record")
public class ReviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long flashcardId;

    private Long userId;

    /** CORRECT / WRONG */
    private String result;

    /** 作答时的主题快照，闪卡主题之后可能被改，历史记录不会被篡改。 */
    private String topic;

    /** 这次作答归属的练习日期。 */
    private LocalDate practiceDate;

    private Integer intervalDays;

    private LocalDateTime reviewedAt;
}
