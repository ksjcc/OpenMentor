package com.dto.review;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 复习训练的前端视图模型，都是扁平字段，方便直接渲染。
 */
public class ReviewPracticeDTO {

    /** 一天练习的概览（计划列表用）。 */
    @Data
    public static class Daily {
        private LocalDate date;
        /** 这一天共多少张 = 已完成 + 还待复习 */
        private int total;
        private int done;
        private int correct;
        private int wrong;
        /** 正确率 0~1，没做过时为空 */
        private Double accuracy;
        /** 还待复习的张数 */
        private int remaining;
        /** DONE / TODAY / UPCOMING / EMPTY */
        private String status;
    }

    /** 一道练习题。 */
    @Data
    public static class Card {
        /** 用字符串传：19 位长整型超出 JS 安全整数范围，前端会被四舍五入。 */
        private String cardId;
        private String question;
        private String answer;
        private String topic;
        private Integer difficulty;
        private Integer masteryLevel;
        /** 这道题今天是否已经答过（历史日期会带结果） */
        private String lastResult;
    }

    /** 某一天的练习详情。 */
    @Data
    public static class Detail {
        private LocalDate date;
        private int total;
        private int done;
        private int correct;
        private int wrong;
        private Double accuracy;
        private int remaining;
        private boolean finished;
        /** 是否可作答（只有今天及以后可以练） */
        private boolean actionable;
        private List<Card> cards = new ArrayList<>();
    }

    /** 提交一次作答后的累计结果。 */
    @Data
    public static class SubmitResult {
        private String cardId;
        private boolean correct;
        /** 本轮（当天的练习）累计 */
        private int done;
        private int correctCount;
        private int wrongCount;
        private Double accuracy;
        private int remaining;
        private boolean finished;
        /** 下一次复习时间（间隔重复排期） */
        private LocalDateTime nextReviewTime;
    }

    /** 「关联知识点」弹窗里的一张历史闪卡。 */
    @Data
    public static class Related {
        /** 同样用字符串，避免前端精度丢失。 */
        private String cardId;
        private String question;
        private String answer;
        private String topic;
        private Integer masteryLevel;
        private LocalDateTime createdAt;
        /** 最近一次作答结果（可能为空） */
        private String lastResult;
        /** 触发这次关联的原因：SAME_TOPIC / GRAPH */
        private String reason;
    }

    /** 关联知识点弹窗的返回。 */
    @Data
    public static class RelatedGroup {
        private String topic;
        /** 图谱里与这个主题相连的其它知识点 */
        private List<String> linkTopics = new ArrayList<>();
        private List<Related> cards = new ArrayList<>();
    }
}
