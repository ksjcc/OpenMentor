package com.dto.resume;

import lombok.Data;

/**
 * 简历新建 / 更新请求。FILE 上传走 multipart 接口，这里只处理文本录入。
 */
@Data
public class ResumeSaveRequest {

    private Long userId;

    private String title;

    private String content;

    private String targetRole;

    /** 保存后是否立即调用模型解析成结构化画像，默认 false。 */
    private Boolean parse;
}
