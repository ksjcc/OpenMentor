package com.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ai.harnessService.InterviewHarnessService;
import com.ai.harnessService.InterviewHarnessService.TurnOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.mq.InterviewPostProcessMessage;
import com.dto.mq.InterviewPostProcessProduce;
import com.entity.InterviewMessage;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.mapper.InterviewMessageMapper;
import com.mapper.InterviewSessionMapper;
import com.service.cache.AiRateLimitService;
import com.service.cache.InterviewMemoryCacheService;

import lombok.RequiredArgsConstructor;

/**
 * 面试业务层。
 *
 * <p>
 * 改造后本类只做三件事：事务与持久化、限流与校验、调用 harness。
 * <b>不包含任何"下一步该做什么"的流程判断</b> —— 那是 harness 里模型的职责。
 */
@Service
@RequiredArgsConstructor
public class InterviewService {

        private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
        private static final String STATUS_FINISHED = "FINISHED";
        private static final String ROLE_AI = "ai";
        private static final String ROLE_USER = "user";
        private static final String AGENT_CODE = "InterviewerAgent";

        private final MemoryService memoryService;
        private final InterviewMessageMapper interviewMessageMapper;
        private final InterviewSessionMapper interviewSessionMapper;
        private final InterviewHarnessService interviewHarnessService;
        private final InterviewPostProcessProduce interviewPostProcessProduce;
        private final InterviewMemoryCacheService interviewMemoryCacheService;
        private final AiRateLimitService aiRateLimitService;
        private final ProfileService profileService;

        /**
         * 创建一场新的面试，并生成第一道问题。
         */
        @Transactional(rollbackFor = Exception.class)
        public SseEmitter startInterviewStream(Long userId, String interviewType, String targetRole, String topic,
                        String projectSummary) {
                SseEmitter emitter = new SseEmitter(0L);
                validateStartRequest(userId, interviewType, targetRole, topic, projectSummary);

                // 1. 创建面试
                InterviewSession session = new InterviewSession();
                session.setUserId(userId);
                session.setInterviewType(interviewType);
                session.setTargetRole(targetRole);
                session.setStatus(STATUS_IN_PROGRESS);
                session.setStartTime(LocalDateTime.now());
                interviewSessionMapper.insert(session);
                Long sessionId = session.getId();
                if (sessionId == null) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "创建面试失败，未生成面试ID");
                }

                // 2. 交给 harness 出第一题（question/answer 都为空 → 由 system prompt 决定开局）
                TurnOutcome outcome = interviewHarnessService.ask(
                                userId, sessionId, topic, null, null, 1);

                if (outcome == null || isBlank(outcome.content())) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成面试问题");
                }

                // 3. 保存 AI 提问
                saveMessage(sessionId, ROLE_AI, AGENT_CODE, outcome.content());

                // 4. 通过 SSE 返回
                //    session 事件让前端拿到本场 sessionId —— 后续回答、结束面试都要用它
                sendEvent(emitter, "session", String.valueOf(sessionId));
                sendEvent(emitter, "action", outcome.action());
                sendEvent(emitter, "chunk", outcome.content());
                emitter.complete();
                return emitter;
        }

        /**
         * 用户回答问题后，由 harness 决定追问 / 换题 / 提难度 / 结束。
         */
        @Transactional(rollbackFor = Exception.class)
        public SseEmitter answerQuestionStream(Long userId, Long sessionId, String topic, String answer) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");
                validateText(topic, "面试主题不能为空");
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
                saveMessage(sessionId, ROLE_USER, "User", answer);

                // 4. 交给 harness：它自己决定调哪些工具、什么时候给最终回复
                TurnOutcome outcome = interviewHarnessService.ask(
                                userId, sessionId, topic, question, answer, nextTurnIndex(sessionId));

                if (outcome == null || isBlank(outcome.content())) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成下一步回复");
                }

                // 5. 保存 AI 新问题 / 追问
                saveMessage(sessionId, ROLE_AI, AGENT_CODE, outcome.content());

                // 6. 返回
                sendEvent(emitter, "action", outcome.action());
                sendEvent(emitter, "chunk", outcome.content());
                if (outcome.scoreJson() != null) {
                        sendEvent(emitter, "score", outcome.scoreJson());
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
                List<InterviewMessage> messages = interviewMessageMapper.selectList(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .orderByAsc(InterviewMessage::getId));
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

                // 4. 更新面试状态
                InterviewSession update = new InterviewSession();
                update.setId(sessionId);
                update.setStatus(STATUS_FINISHED);
                update.setSummary(extractionJson);
                update.setTranscript(transcript);
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
                List<InterviewMessage> cachedMessages = interviewMemoryCacheService.getRecentMessages(sessionId);
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

        private String loadResumeText(Long userId) {
                UserProfile profile = profileService.getProfile(userId);
                return profile == null ? null : profile.getResumeText();
        }

        private String buildProjectSummary(Long userId) {
                UserProfile profile = profileService.getProfile(userId);
                if (profile == null) {
                        return null;
                }
                if (!isBlank(profile.getResumeText())) {
                        return profile.getResumeText();
                }
                return memoryService.getLatestSummaryMd(userId, "overall");
        }

        private void validateStartRequest(Long userId, String interviewType, String targetRole, String topic,
                        String projectSummary) {
                validateRequired(userId, "用户ID不能为空");
                validateText(interviewType, "面试类型不能为空");
                validateText(targetRole, "目标岗位不能为空");
                validateText(topic, "面试主题不能为空");
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

        private boolean isBlank(String value) {
                return value == null || value.isBlank();
        }
}
