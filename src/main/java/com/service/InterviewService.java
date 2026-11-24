package com.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ai.harnessService.InterviewHarnessService;
import com.ai.harnessService.InterviewHarnessService.CandidateContext;
import com.ai.harnessService.InterviewHarnessService.TurnOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.StrengthDTO;
import com.dto.extraction.WeaknessDTO;
import com.dto.interview.InterviewReportDTO;
import com.dto.interview.InterviewSummaryDTO;
import com.dto.interview.ReportDimensionDTO;
import com.dto.interview.ReportFlashcardDTO;
import com.dto.interview.ReportMessageDTO;
import com.dto.mq.InterviewPostProcessMessage;
import com.dto.mq.InterviewPostProcessProduce;
import com.dto.score.ScoreResultDTO;
import com.entity.InterviewMessage;
import com.entity.InterviewSession;
import com.entity.Resume;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.InterviewMessageMapper;
import com.mapper.InterviewSessionMapper;
import com.service.Idempotency.InterviewStartIdempotency;
import com.service.cache.AiRateLimitService;
import com.service.cache.InterviewMemoryCacheService;
import com.service.resume.ResumeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 面试业务层。
 *
 * <p>
 * 本类只做三件事：事务与持久化、限流与校验、调用 harness。
 * <b>不包含任何"下一步该做什么"的流程判断</b> —— 那是 harness 里模型的职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

        private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
        private static final String STATUS_FINISHED = "FINISHED";
        private static final String ROLE_AI = "ai";
        private static final String ROLE_USER = "user";
        private static final String AGENT_CODE = "InterviewerAgent";
        private static final int DEFAULT_LIST_LIMIT = 20;

        private final InterviewMessageMapper interviewMessageMapper;
        private final InterviewSessionMapper interviewSessionMapper;
        private final InterviewHarnessService interviewHarnessService;
        private final InterviewPostProcessProduce interviewPostProcessProduce;
        private final InterviewMemoryCacheService interviewMemoryCacheService;
        private final AiRateLimitService aiRateLimitService;
        private final InterviewStartIdempotency startIdempotency;
        private final ResumeService resumeService;
        private final JsonParseService jsonParseService;
        private final ObjectMapper objectMapper;

        /**
         * 创建一场新的面试，并生成第一道问题。
         *
         * @param resumeId 简历库中的一份简历，注入面试官上下文；为空时使用默认简历
         */
        @Transactional(rollbackFor = Exception.class)
        public SseEmitter startInterviewStream(Long userId, String interviewType, String targetRole, String topic,
                        String projectSummary, Long resumeId, String idempotencyKey) {
                SseEmitter emitter = new SseEmitter(0L);
                validateRequired(userId, "用户ID不能为空");
                validateText(interviewType, "面试类型不能为空");
                validateText(topic, "面试主题不能为空");

                Resume resume = resolveResume(userId, resumeId);
                String effectiveTargetRole = firstNonBlank(targetRole,
                                resume == null ? null : resume.getTargetRole());
                validateText(effectiveTargetRole, "目标岗位不能为空");

                String fingerprint = buildContextFingerprint(projectSummary, resume);
                String scope = startIdempotency.scope(userId, idempotencyKey, interviewType, effectiveTargetRole, topic,
                                fingerprint);
                String existingSessionId = startIdempotency.findExistingSession(scope);
                if (existingSessionId != null) {
                        return replayExisting(emitter, userId, Long.valueOf(existingSessionId));
                }
                String lockToken = startIdempotency.tryLock(scope);
                if (lockToken == null) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "面试正在创建中，请稍候，不要重复提交");
                }
                try {
                        String racedSessionId = startIdempotency.findExistingSession(scope);
                        if (racedSessionId != null) {
                                return replayExisting(emitter, userId, Long.valueOf(racedSessionId));
                        }
                        // 1. 创建面试（主题 / 简历 / 项目摘要都要落库，后续每轮和复盘都要用）
                        InterviewSession session = new InterviewSession();
                        session.setUserId(userId);
                        session.setInterviewType(interviewType);
                        session.setTopic(topic);
                        session.setTargetRole(effectiveTargetRole);
                        session.setResumeId(resume == null ? null : resume.getId());
                        session.setProjectSummary(trimToNull(projectSummary));
                        session.setStatus(STATUS_IN_PROGRESS);
                        session.setStartTime(LocalDateTime.now());
                        interviewSessionMapper.insert(session);
                        Long sessionId = session.getId();
                        if (sessionId == null) {
                                throw new BizException(ErrorCode.INTERNAL_ERROR, "创建面试失败，未生成面试ID");
                        }

                        // 2. 交给 harness 出第一题（question/answer 都为空 → 由 system prompt 决定开局）
                        CandidateContext context = buildCandidateContext(session, topic, resume, projectSummary);
                        TurnOutcome outcome = interviewHarnessService.ask(
                                        userId, sessionId, context, null, null, 1);

                        if (outcome == null || isBlank(outcome.content())) {
                                throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成面试问题");
                        }

                        // 3. 保存 AI 提问
                        saveMessage(sessionId, ROLE_AI, AGENT_CODE, outcome.content());
                        startIdempotency.rememberSession(scope, sessionId, idempotencyKey);
                        // 4. 通过 SSE 返回
                        // session 事件让前端拿到本场 sessionId —— 后续回答、结束面试都要用它
                        sendEvent(emitter, "session", String.valueOf(sessionId));
                        sendEvent(emitter, "action", outcome.action());
                        sendEvent(emitter, "chunk", outcome.content());
                        emitter.complete();
                        return emitter;
                } catch (RuntimeException ex) {
                        // 创建失败要清掉结果键，否则用户重试会拿到一个空会话
                        startIdempotency.unlock(scope, lockToken);
                        throw ex;
                } finally {
                        startIdempotency.unlock(scope, lockToken);
                }
        }

        // 幂等重放：不再新建，直接把已有那场的第一题重新推给前端
        private SseEmitter replayExisting(SseEmitter emitter, Long userId, Long sessionId) {
                InterviewSession session = interviewSessionMapper.selectById(sessionId);
                if (session == null || !userId.equals(session.getUserId())) {
                        throw new BizException(ErrorCode.NOT_FOUND, "幂等记录指向的面试不存在，请重新创建");
                }
                String question = firstQuestion(sessionId);
                sendEvent(emitter, "session", String.valueOf(sessionId));
                sendEvent(emitter, "duplicate", "true");
                sendEvent(emitter, "action", "NEXT");
                sendEvent(emitter, "chunk", question == null ? "这场面试已经创建，请继续作答。" : question);
                emitter.complete();
                return emitter;
        }

        /** 取本场面试的第一条 AI 消息作为开场题。 */
        private String firstQuestion(Long sessionId) {
                List<InterviewMessage> messages = interviewMessageMapper.selectList(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .eq(InterviewMessage::getRole, ROLE_AI)
                                                .orderByAsc(InterviewMessage::getId)
                                                .last("LIMIT 1"));
                return messages == null || messages.isEmpty() ? null : messages.get(0).getContent();
        }

        /**
         * 用户回答问题后，由 harness 决定追问 / 换题 / 提难度 / 结束。
         */
        @Transactional(rollbackFor = Exception.class)
        public SseEmitter answerQuestionStream(Long userId, Long sessionId, String topic, String answer) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");
                validateText(answer, "回答内容不能为空");

                SseEmitter emitter = new SseEmitter(0L);
                InterviewSession session = assertSessionInProgress(userId, sessionId);

                // 1. 限流
                aiRateLimitService.checkLimit(userId);

                // 2. 找出本轮要回答的问题
                String question = resolveCurrentQuestion(sessionId);
                if (isBlank(question)) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "当前面试没有可回答的AI问题");
                }

                // 3. 保存用户回答
                InterviewMessage userMessage = saveMessage(sessionId, ROLE_USER, "User", answer);

                // 4. 交给 harness：它自己决定调哪些工具、什么时候给最终回复
                String effectiveTopic = firstNonBlank(topic, session.getTopic());
                Resume resume = loadResumeQuietly(userId, session.getResumeId());
                CandidateContext context = buildCandidateContext(session, effectiveTopic, resume,
                                session.getProjectSummary());
                TurnOutcome outcome = interviewHarnessService.ask(
                                userId, sessionId, context, question, answer, nextTurnIndex(sessionId));

                if (outcome == null || isBlank(outcome.content())) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成下一步回复");
                }

                // 5. 保存 AI 新问题 / 追问，并落本轮的评分明细
                saveMessage(sessionId, ROLE_AI, AGENT_CODE, outcome.content());
                Integer turnScore = persistTurnScore(userMessage, outcome);
                refreshSessionScore(sessionId);

                // 6. 返回
                sendEvent(emitter, "action", outcome.action());
                sendEvent(emitter, "chunk", outcome.content());
                String scoreText = outcome.scoreObservation() != null
                                ? outcome.scoreObservation()
                                : (turnScore == null ? null : "本轮得分 " + turnScore);
                if (scoreText != null) {
                        sendEvent(emitter, "score", scoreText);
                }
                emitter.complete();
                return emitter;
        }

        public void stopStream(Long userId, Long sessionId) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");
                assertSessionInProgress(userId, sessionId);
        }

        /**
         * 结束面试：拼接全文 → 知识提炼 → 更新状态 → 投递后处理消息。
         */
        @Transactional(rollbackFor = Exception.class)
        public String finishInterview(Long userId, Long sessionId) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");
                InterviewSession current = assertSessionInProgress(userId, sessionId);

                // 1. 查询本场面试所有对话记录
                List<InterviewMessage> messages = listSessionMessages(sessionId);
                if (messages == null || messages.isEmpty()) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "面试没有对话记录，无法结束");
                }

                // 2. 拼接全文
                StringBuilder dialogue = new StringBuilder();
                for (InterviewMessage msg : messages) {
                        dialogue.append(msg.getRole())
                                        .append("（")
                                        .append(msg.getAgentCode())
                                        .append("）：")
                                        .append(msg.getContent())
                                        .append("\n\n");
                }
                String transcript = dialogue.toString();

                // 3. 知识提炼
                String extractionJson = interviewHarnessService.extractKnowledge(userId, sessionId, transcript);
                if (isBlank(extractionJson)) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成知识提炼结果");
                }

                // 4. 更新面试状态（顺手把本场加权总分固化下来）
                InterviewSession update = new InterviewSession();
                update.setId(sessionId);
                update.setStatus(STATUS_FINISHED);
                update.setSummary(extractionJson);
                update.setTranscript(transcript);
                update.setScore(averageScore(messages));
                update.setEndedAt(LocalDateTime.now());
                interviewSessionMapper.updateById(update);

                // 5. 投递后处理消息（画像合并 / 记忆更新 / 闪卡 / 图谱索引）
                InterviewPostProcessMessage message = new InterviewPostProcessMessage();
                message.setUserId(userId);
                message.setSessionId(sessionId);
                message.setExtractionJson(extractionJson);
                sendPostProcessAfterCommit(message);

                return extractionJson;
        }

        /**
         * 旧版无 sessionId 的直接回答接口。
         *
         * <p>
         * 为了不破坏已有调用方保留，但每次调用都是独立的临时会话（没有跨轮记忆），
         * 新代码请用 {@link #answerQuestionStream}。
         */
        public String handleAnswer(Long userId, String topic, String answer) {
                validateRequired(userId, "用户ID不能为空");
                validateText(topic, "面试主题不能为空");
                validateText(answer, "回答内容不能为空");
                aiRateLimitService.checkLimit(userId);

                // null sessionId → harness 内部会生成一次性会话 id，等价于旧版的无状态行为
                TurnOutcome outcome = interviewHarnessService.ask(
                                userId, null, topic, topic, answer, 1);
                if (outcome == null || isBlank(outcome.content())) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成下一步回复");
                }
                return outcome.content();
        }

        // ==================== 我的面试 / 复盘 ====================

        /** 面试记录列表（最近的在前）。 */
        public List<InterviewSummaryDTO> listInterviews(Long userId, Integer limit) {
                validateRequired(userId, "用户ID不能为空");
                int size = limit == null || limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, 100);
                List<InterviewSession> sessions = interviewSessionMapper.selectList(
                                new LambdaQueryWrapper<InterviewSession>()
                                                .eq(InterviewSession::getUserId, userId)
                                                .orderByDesc(InterviewSession::getId)
                                                .last("LIMIT " + size));
                if (sessions == null || sessions.isEmpty()) {
                        return List.of();
                }

                List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
                List<InterviewMessage> allMessages = interviewMessageMapper.selectList(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .in(InterviewMessage::getSessionId, sessionIds)
                                                .orderByAsc(InterviewMessage::getId));

                Map<Long, int[]> counters = new LinkedHashMap<>();
                for (InterviewMessage message : allMessages) {
                        int[] counter = counters.computeIfAbsent(message.getSessionId(), key -> new int[1]);
                        if (ROLE_AI.equals(message.getRole())) {
                                counter[0]++;
                        }
                }

                List<InterviewSummaryDTO> result = new ArrayList<>(sessions.size());
                for (InterviewSession session : sessions) {
                        InterviewSummaryDTO dto = new InterviewSummaryDTO();
                        dto.setSessionId(session.getId());
                        dto.setTopic(session.getTopic());
                        dto.setTargetRole(session.getTargetRole());
                        dto.setInterviewType(session.getInterviewType());
                        dto.setStatus(session.getStatus());
                        dto.setScore(session.getScore());
                        dto.setTurns(counters.getOrDefault(session.getId(), new int[1])[0]);
                        dto.setStartedAt(session.getStartTime());
                        dto.setEndedAt(session.getEndedAt());
                        dto.setTopWeakness(firstWeakness(session.getSummary()));
                        result.add(dto);
                }
                return result;
        }

        /** 单场复盘报告。 */
        public InterviewReportDTO getInterviewReport(Long userId, Long sessionId) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");

                InterviewSession session = interviewSessionMapper.selectById(sessionId);
                if (session == null) {
                        throw new BizException(ErrorCode.NOT_FOUND, "面试不存在");
                }
                if (!userId.equals(session.getUserId())) {
                        throw new BizException(ErrorCode.UNAUTHORIZED, "无权访问该面试");
                }

                List<InterviewMessage> messages = listSessionMessages(sessionId);
                KnowledgeExtractionResult extraction = parseExtractionQuietly(session.getSummary());

                InterviewReportDTO report = new InterviewReportDTO();
                report.setSessionId(sessionId);
                report.setTopic(session.getTopic());
                report.setTargetRole(session.getTargetRole());
                report.setInterviewType(session.getInterviewType());
                report.setStatus(session.getStatus());
                report.setScore(session.getScore() != null ? session.getScore() : averageScore(messages));
                report.setStartedAt(session.getStartTime());
                report.setEndedAt(session.getEndedAt());
                report.setResumeTitle(resumeTitleOf(userId, session.getResumeId()));
                report.setDurationSec(durationSeconds(session));
                report.setTurns(countAiMessages(messages));

                if (extraction != null) {
                        report.setSummary(extraction.getSummary());
                        if (extraction.getStrengths() != null) {
                                report.setStrengths(extraction.getStrengths().stream()
                                                .map(this::renderStrength)
                                                .filter(item -> !isBlank(item))
                                                .toList());
                        }
                        if (extraction.getWeaknesses() != null) {
                                report.setWeaknesses(extraction.getWeaknesses().stream()
                                                .map(this::renderWeakness)
                                                .filter(item -> !isBlank(item))
                                                .toList());
                        }
                        if (extraction.getFlashcards() != null) {
                                report.setFlashcards(extraction.getFlashcards().stream()
                                                .filter(card -> card != null && !isBlank(card.getQuestion()))
                                                .map(card -> ReportFlashcardDTO.of(
                                                                card.getQuestion(),
                                                                card.getAnswer(),
                                                                firstNonBlank(session.getTopic(), "知识点")))
                                                .toList());
                        }
                        if (extraction.getNextPlan() != null) {
                                report.setNextPlan(extraction.getNextPlan().stream()
                                                .filter(item -> !isBlank(item))
                                                .toList());
                        }
                }

                report.setDimensions(buildDimensions(messages));
                report.setMessages(messages.stream()
                                .map(message -> ReportMessageDTO.of(
                                                message.getRole(),
                                                message.getContent(),
                                                message.getScore()))
                                .toList());
                return report;
        }

        // ==================== 上下文构建 ====================

        private Resume resolveResume(Long userId, Long resumeId) {
                if (resumeId != null) {
                        // resumeId = 0 表示用户显式选择「不关联简历」，此时不要回落到默认简历
                        return resumeId == 0L ? null : resumeService.get(userId, resumeId);
                }
                return resumeService.findDefault(userId);
        }

        private Resume loadResumeQuietly(Long userId, Long resumeId) {
                if (resumeId == null) {
                        return null;
                }
                try {
                        return resumeService.get(userId, resumeId);
                } catch (RuntimeException ex) {
                        log.warn("加载简历失败，本轮不注入简历 userId={} resumeId={}", userId, resumeId, ex);
                        return null;
                }
        }

        private String resumeTitleOf(Long userId, Long resumeId) {
                Resume resume = loadResumeQuietly(userId, resumeId);
                return resume == null ? null : resume.getTitle();
        }

        private CandidateContext buildCandidateContext(InterviewSession session, String topic, Resume resume,
                        String projectSummary) {
                return new CandidateContext(
                                session.getInterviewType(),
                                session.getTargetRole(),
                                topic,
                                resume == null ? null : resume.getTitle(),
                                resume == null ? null : resume.getContent(),
                                projectSummary);
        }

        /** 幂等指纹：同参数 + 同简历才算同一次创建。 */
        private String buildContextFingerprint(String projectSummary, Resume resume) {
                String summary = projectSummary == null ? "" : projectSummary.trim();
                if (summary.length() > 32) {
                        summary = summary.substring(0, 32);
                }
                return summary + "|r=" + (resume == null ? "-" : resume.getId());
        }

        // ==================== 评分 ====================

        /** 把本轮评分写回用户消息，返回本轮总分。 */
        private Integer persistTurnScore(InterviewMessage answerMessage, TurnOutcome outcome) {
                Integer score = outcome.score();
                if (score == null || answerMessage == null || answerMessage.getId() == null) {
                        return null;
                }
                InterviewMessage patch = new InterviewMessage();
                patch.setId(answerMessage.getId());
                patch.setScore(score);
                if (outcome.scoreResult() != null) {
                        try {
                                patch.setScoreDetail(objectMapper.writeValueAsString(outcome.scoreResult()));
                        } catch (Exception ex) {
                                log.warn("评分明细序列化失败 messageId={}", answerMessage.getId(), ex);
                        }
                }
                interviewMessageMapper.updateById(patch);
                answerMessage.setScore(score);
                return score;
        }

        /** 会话总分 = 已评分轮次的平均分。 */
        private void refreshSessionScore(Long sessionId) {
                List<InterviewMessage> messages = listSessionMessages(sessionId);
                Integer average = averageScore(messages);
                if (average == null) {
                        return;
                }
                InterviewSession patch = new InterviewSession();
                patch.setId(sessionId);
                patch.setScore(average);
                interviewSessionMapper.updateById(patch);
        }

        private Integer averageScore(List<InterviewMessage> messages) {
                if (messages == null || messages.isEmpty()) {
                        return null;
                }
                int sum = 0;
                int count = 0;
                for (InterviewMessage message : messages) {
                        if (message.getScore() != null) {
                                sum += message.getScore();
                                count++;
                        }
                }
                return count == 0 ? null : Math.round((float) sum / count);
        }

        private List<ReportDimensionDTO> buildDimensions(List<InterviewMessage> messages) {
                if (messages == null || messages.isEmpty()) {
                        return List.of();
                }
                int technicalSum = 0;
                int technicalCount = 0;
                int completenessSum = 0;
                int completenessCount = 0;
                int expressionSum = 0;
                int expressionCount = 0;

                for (InterviewMessage message : messages) {
                        if (isBlank(message.getScoreDetail())) {
                                continue;
                        }
                        ScoreResultDTO detail;
                        try {
                                detail = objectMapper.readValue(message.getScoreDetail(), ScoreResultDTO.class);
                        } catch (Exception ex) {
                                continue;
                        }
                        if (detail.getTechnical() != null) {
                                technicalSum += detail.getTechnical();
                                technicalCount++;
                        }
                        if (detail.getCompleteness() != null) {
                                completenessSum += detail.getCompleteness();
                                completenessCount++;
                        }
                        if (detail.getExpression() != null) {
                                expressionSum += detail.getExpression();
                                expressionCount++;
                        }
                }

                List<ReportDimensionDTO> dimensions = new ArrayList<>();
                if (technicalCount > 0) {
                        dimensions.add(ReportDimensionDTO.of("技术正确性", average(technicalSum, technicalCount)));
                }
                if (completenessCount > 0) {
                        dimensions.add(ReportDimensionDTO.of("回答完整性", average(completenessSum, completenessCount)));
                }
                if (expressionCount > 0) {
                        dimensions.add(ReportDimensionDTO.of("表达能力", average(expressionSum, expressionCount)));
                }
                return dimensions;
        }

        private Integer average(int sum, int count) {
                return count == 0 ? null : Math.round((float) sum / count);
        }

        // ==================== 复盘解析 ====================

        private KnowledgeExtractionResult parseExtractionQuietly(String summaryJson) {
                if (isBlank(summaryJson)) {
                        return null;
                }
                try {
                        return jsonParseService.parseExtractionLenient(summaryJson);
                } catch (Exception ex) {
                        log.warn("复盘提炼结果解析失败，报告降级为只展示对话", ex);
                        return null;
                }
        }

        private String firstWeakness(String summaryJson) {
                KnowledgeExtractionResult extraction = parseExtractionQuietly(summaryJson);
                if (extraction == null || extraction.getWeaknesses() == null
                                || extraction.getWeaknesses().isEmpty()) {
                        return null;
                }
                WeaknessDTO weakness = extraction.getWeaknesses().get(0);
                return weakness == null ? null : trimToNull(weakness.getName());
        }

        private String renderStrength(StrengthDTO strength) {
                if (strength == null || isBlank(strength.getName())) {
                        return null;
                }
                return isBlank(strength.getEvidence())
                                ? strength.getName().trim()
                                : strength.getName().trim() + "：" + strength.getEvidence().trim();
        }

        private String renderWeakness(WeaknessDTO weakness) {
                if (weakness == null || isBlank(weakness.getName())) {
                        return null;
                }
                return isBlank(weakness.getEvidence())
                                ? weakness.getName().trim()
                                : weakness.getName().trim() + "：" + weakness.getEvidence().trim();
        }

        private Long durationSeconds(InterviewSession session) {
                if (session.getStartTime() == null) {
                        return null;
                }
                LocalDateTime end = session.getEndedAt() != null ? session.getEndedAt() : LocalDateTime.now();
                return Math.max(0, Duration.between(session.getStartTime(), end).getSeconds());
        }

        private int countAiMessages(List<InterviewMessage> messages) {
                if (messages == null) {
                        return 0;
                }
                return (int) messages.stream().filter(message -> ROLE_AI.equals(message.getRole())).count();
        }

        private List<InterviewMessage> listSessionMessages(Long sessionId) {
                List<InterviewMessage> messages = interviewMessageMapper.selectList(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .orderByAsc(InterviewMessage::getId));
                return messages == null ? Collections.emptyList() : messages;
        }

        // ==================== 持久化与缓存 ====================

        /**
         * 保存一条面试聊天记录，并在事务提交后写 Redis 短期缓存。
         */
        public InterviewMessage saveMessage(Long sessionId, String role, String agentCode, String content) {
                validateRequired(sessionId, "面试ID不能为空");
                validateText(role, "消息角色不能为空");
                validateText(content, "消息内容不能为空");

                InterviewMessage message = new InterviewMessage();
                message.setSessionId(sessionId);
                message.setRole(role);
                message.setAgentCode(agentCode);
                message.setContent(content);
                interviewMessageMapper.insert(message);

                cacheMessageAfterCommit(sessionId, message);
                return message;
        }

        private void cacheMessageAfterCommit(Long sessionId, InterviewMessage message) {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                        interviewMemoryCacheService.cacheMessage(sessionId, message);
                        return;
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                                interviewMemoryCacheService.cacheMessage(sessionId, message);
                        }
                });
        }

        private void sendPostProcessAfterCommit(InterviewPostProcessMessage message) {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                        interviewPostProcessProduce.send(message);
                        return;
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                                interviewPostProcessProduce.send(message);
                        }
                });
        }

        // ==================== 上下文读取 ====================

        /**
         * 本轮要回答的问题 = 最近一条 AI 消息。
         *
         * 这里只负责"找出问题"，不再自己拼接 prompt ——
         * 上下文由 harness 的工具按需去取。
         */
        private String resolveCurrentQuestion(Long sessionId) {
                List<InterviewMessage> recentMessages = getRecentMessagesForPrompt(sessionId);
                InterviewMessage lastAiQuestion = getLastAiMessage(recentMessages);
                if (lastAiQuestion == null) {
                        lastAiQuestion = getLastAiMessage(getRecentMessagesFromDatabase(sessionId));
                }
                return lastAiQuestion == null ? null : lastAiQuestion.getContent();
        }

        private List<InterviewMessage> getRecentMessagesForPrompt(Long sessionId) {
                List<InterviewMessage> cachedMessages = interviewMemoryCacheService
                                .getRecentMessages(sessionId);
                if (cachedMessages != null && !cachedMessages.isEmpty()) {
                        return cachedMessages;
                }
                return getRecentMessagesFromDatabase(sessionId);
        }

        private List<InterviewMessage> getRecentMessagesFromDatabase(Long sessionId) {
                List<InterviewMessage> messages = interviewMessageMapper.selectList(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .orderByDesc(InterviewMessage::getId)
                                                .last("LIMIT 20"));
                if (messages == null || messages.isEmpty()) {
                        return Collections.emptyList();
                }
                List<InterviewMessage> orderedMessages = new ArrayList<>(messages);
                Collections.reverse(orderedMessages);
                return orderedMessages;
        }

        private InterviewMessage getLastAiMessage(List<InterviewMessage> messages) {
                if (messages == null || messages.isEmpty()) {
                        return null;
                }
                for (int index = messages.size() - 1; index >= 0; index--) {
                        InterviewMessage message = messages.get(index);
                        if (message != null && ROLE_AI.equals(message.getRole())) {
                                return message;
                        }
                }
                return null;
        }

        /** 第几轮 = 已经说过多少条 AI 消息 + 1。 */
        private int nextTurnIndex(Long sessionId) {
                Long aiCount = interviewMessageMapper.selectCount(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .eq(InterviewMessage::getRole, ROLE_AI));
                return (aiCount == null ? 0 : aiCount.intValue()) + 1;
        }

        // ==================== SSE ====================

        private void sendEvent(SseEmitter emitter, String name, String data) {
                if (data == null) {
                        return;
                }
                try {
                        emitter.send(SseEmitter.event().name(name).data(data));
                } catch (IOException e) {
                        throw new IllegalStateException("SSE send failed: " + name, e);
                }
        }

        // ==================== 校验与查询 ====================

        private InterviewSession assertSessionInProgress(Long userId, Long sessionId) {
                InterviewSession session = interviewSessionMapper.selectById(sessionId);
                if (session == null) {
                        throw new BizException(ErrorCode.NOT_FOUND, "面试不存在");
                }
                if (!userId.equals(session.getUserId())) {
                        throw new BizException(ErrorCode.UNAUTHORIZED, "无权访问该面试");
                }
                if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "面试已结束");
                }
                return session;
        }

        private void validateRequired(Object value, String message) {
                if (value == null) {
                        throw new BizException(ErrorCode.BAD_REQUEST, message);
                }
        }

        private void validateText(String value, String message) {
                if (isBlank(value)) {
                        throw new BizException(ErrorCode.BAD_REQUEST, message);
                }
        }

        private String firstNonBlank(String first, String second) {
                if (!isBlank(first)) {
                        return first.trim();
                }
                return isBlank(second) ? null : second.trim();
        }

        private String trimToNull(String value) {
                return isBlank(value) ? null : value.trim();
        }

        private boolean isBlank(String value) {
                return value == null || value.isBlank();
        }
}
