package com.ai.prompt;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DefaultPromptBudgetAllocator implements PromptBudgetAllocator {
  private static final int DEFAULT_RECENT_LIMIT = 500;
  private static final int DEFAULT_SUMMARY_LIMIT = 800;
  private static final int DEFAULT_PROFILE_LIMIT = 600;
  private static final int DEFAULT_GRAPH_REASONING_LIMIT = 600;
  private static final int DEFAULT_RESUME_LIMIT = 1000;
  private static final int DEFAULT_PROJECT_SUMMARY_LIMIT = 1000;

  @Override
  public PromptContext compress(PromptContext ctx,
      PromptActionType action) {
    PromptContext safeContext = ctx == null ? PromptContext.builder().build() : ctx.toBuilder().build();
    PromptActionType effectiveAction = action == null ? PromptActionType.NEW_QUESTION : action;

    return switch (effectiveAction) {

      case FOLLOW_UP ->
        compressFollowUp(safeContext);

      case NEXT, HARDER, NEW_QUESTION ->
        compressKnowledge(safeContext);

      case SCORE ->
        compressScore(safeContext);

      case DECISION ->
        compressDecision(safeContext);

      case EXTRACTION ->
        compressExtraction(safeContext);

      case PROFILE ->
        compressProfile(safeContext);

      case GRAPH_EXTRACTION, COMMUNITY_REPORT, DRIFT_QUERY ->
        compressGraphTask(safeContext);
    };
  }

  private String trim(
      String text,
      int limit) {

    if (text == null) {
      return "";
    }

    if (text.length() <= limit) {
      return text;
    }

    return text.substring(0, limit);
  }

  private PromptContext compressFollowUp(
      PromptContext ctx) {

    ctx.setRecentContext(
        trim(
            ctx.getRecentContext(),
            1600));

    ctx.setRagMemories(
        trimList(
            ctx.getRagMemories(),
            4,
            450));

    ctx.setSummaryMd(
        trim(
            ctx.getSummaryMd(),
            800));

    ctx.setProfileJson(
        trim(
            ctx.getProfileJson(),
            500));

    ctx.setGraphReasoning(
        trim(
            ctx.getGraphReasoning(),
            800));

    ctx.setResumeText(
        trim(
            ctx.getResumeText(),
            900));

    ctx.setProjectSummary(
        trim(
            ctx.getProjectSummary(),
            900));

    return ctx;
  }

  private PromptContext compressKnowledge(
      PromptContext ctx) {

    ctx.setRagMemories(
        trimList(
            ctx.getRagMemories(),
            5,
            500));

    ctx.setSummaryMd(
        trim(
            ctx.getSummaryMd(),
            1200));

    ctx.setProfileJson(
        trim(
            ctx.getProfileJson(),
            800));

    ctx.setRecentContext(
        trim(
            ctx.getRecentContext(),
            500));

    ctx.setGraphReasoning(
        trim(
            ctx.getGraphReasoning(),
            DEFAULT_GRAPH_REASONING_LIMIT));

    ctx.setResumeText(
        trim(
            ctx.getResumeText(),
            DEFAULT_RESUME_LIMIT));

    ctx.setProjectSummary(
        trim(
            ctx.getProjectSummary(),
            DEFAULT_PROJECT_SUMMARY_LIMIT));

    return ctx;
  }

  private PromptContext compressScore(
      PromptContext ctx) {

    ctx.setRagMemories(
        trimList(
            ctx.getRagMemories(),
            2,
            250));

    ctx.setSummaryMd(
        trim(
            ctx.getSummaryMd(),
            300));

    ctx.setRecentContext(
        trim(
            ctx.getRecentContext(),
            500));

    ctx.setProfileJson(
        trim(
            ctx.getProfileJson(),
            300));

    return ctx;
  }

  private PromptContext compressDecision(PromptContext ctx) {
    ctx.setRagMemories(trimList(ctx.getRagMemories(), 3, 300));
    ctx.setSummaryMd(trim(ctx.getSummaryMd(), 500));
    ctx.setRecentContext(trim(ctx.getRecentContext(), 700));
    ctx.setProfileJson(trim(ctx.getProfileJson(), 400));
    return ctx;
  }

  private PromptContext compressExtraction(PromptContext ctx) {
    ctx.setDialogue(trim(ctx.getDialogue(), 5000));
    return ctx;
  }

  private PromptContext compressProfile(PromptContext ctx) {
    ctx.setResumeText(trim(ctx.getResumeText(), 4000));
    ctx.setTargetRole(trim(ctx.getTargetRole(), 120));
    return ctx;
  }

  private PromptContext compressGraphTask(PromptContext ctx) {
    ctx.setRawInput(trim(ctx.getRawInput(), 4000));
    return ctx;
  }

  private List<String> trimList(List<String> values, int maxItems, int perItemLimit) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    List<String> trimmed = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      trimmed.add(trim(value, perItemLimit));
      if (trimmed.size() >= maxItems) {
        break;
      }
    }
    return trimmed;
  }
}
