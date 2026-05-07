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
}
