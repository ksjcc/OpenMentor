package com.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("interview_message")
public class InterviewMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String agentCode;
    private String content;
    /** 本轮回答的得分（仅 user 消息有值），供复盘统计使用。 */
    private Integer score;
    /** 本轮评分的完整明细 JSON（技术/完整/表达）。 */
    private String scoreDetail;
}
