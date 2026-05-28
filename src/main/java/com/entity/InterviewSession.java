package com.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("interview_session")
public class InterviewSession {

    private Long id;
    private Long userId;
    private String interviewType;
    private String targetRole;
    private String status;
    private Integer score;
    private String summary;
    /**
     * 面试后处理状态：PENDING / PROCESSING / SUCCESS / FAILED。
     */
    private String postProcessStatus;
    /**
     * 面试后处理已重试次数。
     */
    private Integer postProcessRetryCount;
    /**
     * 最近一次后处理失败原因。
     */
    private String postProcessError;
    /**
     * 最近一次后处理完成时间。
     */
    private LocalDateTime postProcessFinishedAt;
    /**
     * 本场面试完整对话原文，供复盘及后续索引使用。
     */
    private String transcript;
    @TableField("created_at")
    private LocalDateTime startTime;
    private LocalDateTime endedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getScore() {
        return score;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPostProcessStatus() {
        return postProcessStatus;
    }

    public void setPostProcessStatus(String postProcessStatus) {
        this.postProcessStatus = postProcessStatus;
    }

    public Integer getPostProcessRetryCount() {
        return postProcessRetryCount;
    }

    public void setPostProcessRetryCount(Integer postProcessRetryCount) {
        this.postProcessRetryCount = postProcessRetryCount;
    }

    public String getPostProcessError() {
        return postProcessError;
    }

    public void setPostProcessError(String postProcessError) {
        this.postProcessError = postProcessError;
    }

    public LocalDateTime getPostProcessFinishedAt() {
        return postProcessFinishedAt;
    }

    public void setPostProcessFinishedAt(LocalDateTime postProcessFinishedAt) {
        this.postProcessFinishedAt = postProcessFinishedAt;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getInterviewType() {
        return interviewType;
    }

    public void setInterviewType(String interviewType) {
        this.interviewType = interviewType;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

}
