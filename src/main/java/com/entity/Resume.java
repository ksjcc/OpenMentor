package com.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户简历库中的一份简历。
 *
 * <p>
 * 一个用户可以有多份简历（按岗位准备不同版本），创建面试时选择其中一份注入
 * 面试官的上下文；其中一份可以标记为默认。
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 简历名称，例如「Java 后端 · 2026 春招」。 */
    private String title;

    /** 简历正文（纯文本）。上传 pdf/docx 时为抽取出来的文本。 */
    private String content;

    /** 模型解析出来的结构化画像 JSON。解析失败时为 null。 */
    private String parsedJson;

    /** 上传时的原始文件名，手动录入时为 null。 */
    private String fileName;

    /** 来源类型：TEXT / PDF / DOCX / TXT。 */
    private String fileType;

    /** 这份简历对应的目标岗位，用于创建面试时预填。 */
    private String targetRole;

    /** 1 = 默认简历，创建面试时自动选中。 */
    private Integer isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 是否已有结构化解析结果。
     *
     * <p>
     * 列表接口不返回 parsedJson（可能几 KB），只回这个标记；详情接口才带全量。
     * 非表字段。
     */
    @TableField(exist = false)
    private Boolean hasParsed;
}
