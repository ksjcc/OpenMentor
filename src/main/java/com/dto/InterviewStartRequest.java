package com.dto;

public record InterviewStartRequest(
    Long userId,
    String targetRole,
    Integer questionCount
) {
}
