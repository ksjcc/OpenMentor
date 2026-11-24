package com.dto.dashboard;

import lombok.Data;

import java.util.List;

/**
 * Dashboard 聚合返回对象
 *
 * 用于首页成长驾驶舱展示。
 */
@Data
public class DashboardDTO {
  /**
   * 用户能力画像
   */
  private List<SkillRadarDTO> skills;

  /**
   * 最近薄弱点排行
   */
  private List<WeaknessRankDTO> weaknesses;

  /**
   * 最近面试记录
   */
  private List<RecentInterviewDTO> recentInterviews;

  /**
   * 今日待复习数量
   */
  private Integer todayReviewCount;

  /**
   * AI成长建议
   */
  private List<String> aiSuggestions;
  private LearningRecommendationDTO learningRecommendation;

  /**
   * 累计面试场次
   */
  private Integer totalSessions;

  /**
   * 平均得分（只统计已有评分的场次）
   */
  private Integer averageScore;

  /**
   * 最近若干场的得分序列，用于趋势图
   */
  private List<Integer> scoreTrend;

  /**
   * 与 scoreTrend 一一对应的横轴标签（MM-dd）
   */
  private List<String> trendLabels;
}
