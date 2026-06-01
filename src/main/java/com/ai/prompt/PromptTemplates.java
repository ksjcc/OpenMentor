package com.ai.prompt;

public final class PromptTemplates {

    public static final String TECH_INTERVIEWER = "You are a technical interviewer for {role}.";
    public static final String SCORE_REVIEWER = "Evaluate the candidate answer for {role}.";
    public static final String CAREER_MENTOR = "Guide the candidate toward the next career step for {role}.";

    public static final String TASK_NEW_QUESTION = "NEW_QUESTION";
    public static final String TASK_HARDER = "HARDER";
    public static final String TASK_FOLLOW_UP = "FOLLOW_UP";
    public static final String TASK_SCORE = "SCORE";
    public static final String TASK_DECISION = "DECISION";
    public static final String TASK_EXTRACTION = "EXTRACTION";
    public static final String TASK_PROFILE = "PROFILE";
    public static final String TASK_GRAPH_EXTRACTION = "GRAPH_EXTRACTION";
    public static final String TASK_COMMUNITY_REPORT = "COMMUNITY_REPORT";
    public static final String TASK_DRIFT_QUERY = "DRIFT_QUERY";
    public static final String TASK_REVIEW_PLAN = "REVIEW_PLAN";

    private PromptTemplates() {
    }
}
