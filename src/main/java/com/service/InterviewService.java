package com.service;

import com.common.BizException;
import com.common.ErrorCode;
import com.ai.agent.TechInterviewerAgent;
import com.ai.orchestrator.AgentOrchestrator;
import com.ai.orchestrator.OrchestratorResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.InterviewReplyDTO;
import com.dto.graph.KnowledgeGraphBuilder;
import com.entity.InterviewMessage;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.ScoreAgent;
import com.mapper.InterviewMessageMapper;
import com.mapper.InterviewSessionMapper;
import com.service.Dashboard.FlashcardService;
import com.service.cache.AiRateLimitService;
import com.service.cache.InterviewMemoryCacheService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dto.mq.InterviewPostProcessMessage;
import com.dto.mq.InterviewPostProcessProduce;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewService {

        private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
        private static final String STATUS_FINISHED = "FINISHED";
        private static final String ROLE_AI = "ai";
        private static final String ROLE_USER = "user";
        private static final String ACTION_FOLLOW_UP = "FOLLOW_UP";
        private static final String ACTION_QUESTION = "QUESTION";
        private static final String ACTION_NEXT = "NEXT";

        private final TechInterviewerAgent interviewerAgent;
        private final ScoreAgent scoreAgent;
        private final FollowUpAgent followUpAgent;
        private final MemoryService memoryService;
        private final InterviewMessageMapper interviewMessageMapper;
        private final InterviewSessionMapper interviewSessionMapper;
        private final AgentOrchestrator agentOrchestrator;
        private final FlashcardService flashcardService;
        private final JsonParseService jsonParseService;
        private final KnowledgeGraphBuilder knowledgeGraphBuilder;
        private final InterviewPostProcessProduce interviewPostProcessProduce;
        private final InterviewMemoryCacheService interviewMemoryCacheService;
        private final AiRateLimitService aiRateLimitService;
        private final ProfileService profileService;

        /**
         * 创建一场新的面试，并生成第一道问题
         *
         * @param userId        用户ID
         * @param interviewType 面试类型：TECH / PROJECT / HR
         * @param targetRole    目标岗位，例如 Java后端开发
         * @param topic         当前面试主题，例如 Redis
         * @return 第一题
         */
        @Transactional(rollbackFor = Exception.class)
        public InterviewReplyDTO startInterview(Long userId, String interviewType, String targetRole, String topic,
                        String projectSummary) {
                validateStartRequest(userId, interviewType, targetRole, topic, projectSummary);

                // 1.创建面试
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

                // 2.调用agent编排器生成第一题目
                String question = agentOrchestrator.generateFirstQuestion(userId, topic, targetRole, projectSummary);
                if (isBlank(question)) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成面试问题");
                }

                // 3.保存 AI 提问到 interview_message
                saveMessage(
                                sessionId,
                                ROLE_AI,
                                "TechInterviewerAgent",
                                question);

                // 4. 返回给前端
                InterviewReplyDTO reply = new InterviewReplyDTO();
                reply.setSessionId(sessionId);
                reply.setAction(ACTION_QUESTION);
                reply.setContent(question);
                return reply;
        }

        /**
         * 用户回答问题后，系统评分并生成追问或下一题
         *
         * @param userId    用户ID
         * @param sessionId 本场面试ID
         * @param topic     当前主题
         * @param answer    用户回答
         * @return AI下一步回复
         */
        @Transactional(rollbackFor = Exception.class)
        public InterviewReplyDTO answerQuestion(
                        Long userId,
                        Long sessionId,
                        String topic,
                        String answer) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");
                validateText(topic, "面试主题不能为空");
                validateText(answer, "回答内容不能为空");
                InterviewSession session = assertSessionInProgress(userId, sessionId);

                // 1. AI 调用限流：本接口会触发评分 Agent、追问决策 Agent、面试官 Agent。
                // 超过每分钟 10 次时直接抛出业务异常，不再继续写入回答或调用 AI。
                aiRateLimitService.checkLimit(userId);

                // 2. 从 Redis 获取最近 20 条短期上下文。
                // Redis 命中时可减少 interview_message 表查询；Redis 未命中时再降级查 MySQL。
                List<InterviewMessage> recentMessages = getRecentMessagesForPrompt(sessionId);

                // 3. 获取上一条 AI 问题
                // 因为评分需要知道：用户是在回答哪个问题

                InterviewMessage lastAiQuestion = getLastAiMessage(recentMessages);
                if (lastAiQuestion == null) {
                        recentMessages = getRecentMessagesFromDatabase(sessionId);
                        lastAiQuestion = getLastAiMessage(recentMessages);
                }
                if (lastAiQuestion == null || isBlank(lastAiQuestion.getContent())) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "当前面试没有可回答的AI问题");
                }
                String question = lastAiQuestion.getContent();

                // 4. 保存用户回答到 MySQL，并在事务提交后写入 Redis 短期记忆。
                saveMessage(
                                sessionId,
                                ROLE_USER,
                                "User",
                                answer);

                // 6. 调用 AgentOrchestrator：
                // 评分 → 决策 → 生成追问/下一题
                OrchestratorResult result = agentOrchestrator.handleAnswerWithMultiAgentDecision(userId, sessionId,
                                topic,
                                session.getTargetRole(),
                                loadResumeText(userId),
                                buildProjectSummary(userId),
                                question, answer, memoryService.getLatestProfileJson(userId),
                                memoryService.getLatestSummaryMd(userId, "overall"));
                InterviewReplyDTO reply = new InterviewReplyDTO();
                reply.setSessionId(sessionId);
                // 7. 保存 AI 新问题 / 追问
                reply.setAction(result.getAction());
                reply.setContent(result.getContent());
                reply.setScoreJson(result.getScoreJson());
                reply.setStageLatencyMs(result.getStageLatencyMs());
                reply.setTotalLatencyMs(result.getTotalLatencyMs());
                saveMessage(
                                sessionId,
                                "ai",
                                "MultiAgentOrchestrator",
                                reply.getContent());
                // 第一版先不单独保存 score 到 interview_score 表
                // 后面可以把 reply.getScoreJson() 解析后写入评分表

                return reply;
        }

        /**
         * 结束面试：
         * 1. 查询完整对话
         * 2. 调用 KnowledgeExtractorAgent 提炼知识
         * 3. 更新长期记忆
         * 4. 生成闪卡
         * 5. 更新 session 状态
         *
         * @param userId    用户ID
         * @param sessionId 面试ID
         * @return 知识提炼结果 JSON
         */
        @Transactional(rollbackFor = Exception.class)
        public String finishInterview(Long userId, Long sessionId) {
                validateRequired(userId, "用户ID不能为空");
                validateRequired(sessionId, "面试ID不能为空");
                assertSessionInProgress(userId, sessionId);

                // 1. 查询本场面试所有对话记录
                List<InterviewMessage> messages = interviewMessageMapper.selectList(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .orderByAsc(InterviewMessage::getId));
                if (messages == null || messages.isEmpty()) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "面试没有对话记录，无法结束");
                }

                // 2. 拼接成完整对话文本
                StringBuilder dialogue = new StringBuilder();

                for (InterviewMessage msg : messages) {
                        dialogue.append(msg.getRole())
                                        .append("（")
                                        .append(msg.getAgentCode())
                                        .append("）：")
                                        .append(msg.getContent())
                                        .append("\n\n");
                }

                // 3. 调用知识提炼 Agent
                String transcript = dialogue.toString();
                String extractionJson = agentOrchestrator.extractKnowledge(transcript);
                if (isBlank(extractionJson)) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成知识提炼结果");
                }

                InterviewSession session = new InterviewSession();
                session.setId(sessionId);
                session.setStatus(STATUS_FINISHED);
                session.setSummary(extractionJson);
                session.setTranscript(transcript);
                session.setEndedAt(LocalDateTime.now());
                interviewSessionMapper.updateById(session);
                // 4. 发送 MQ 消息，异步处理后续任务
                InterviewPostProcessMessage message = new InterviewPostProcessMessage();
                message.setUserId(userId);
                message.setSessionId(sessionId);
                message.setExtractionJson(extractionJson);
                sendPostProcessAfterCommit(message);
                return extractionJson;
        }

        /**
         * 用户回答后处理
         */
        public String handleAnswer(Long userId, String topic, String answer) {
                validateRequired(userId, "用户ID不能为空");
                validateText(topic, "面试主题不能为空");
                validateText(answer, "回答内容不能为空");

                // 旧版直接回答接口同样会调用 AI，因此复用同一个用户级限流规则。
                aiRateLimitService.checkLimit(userId);

                InterviewReplyDTO reply = agentOrchestrator.handleAnswer(
                                userId,
                                topic,
                                topic,
                                answer);
                if (reply == null || isBlank(reply.getContent())) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成下一步回复");
                }

                return reply.getContent();
        }

        /**
         * 结束一场面试，进行知识提炼 + 记忆更新
         *
         * @param userId    用户ID
         * @param sessionId 本次面试ID（核心）
         * @return 提炼后的JSON结果
         */
        // public String finishInterview(Long userId, Long sessionId) {
        // // 查询本次面试过程所有的对话
        // LambdaQueryWrapper<InterviewMessage> wrapper = new LambdaQueryWrapper();
        // wrapper.eq(InterviewMessage::getSessionId, sessionId)
        // .orderByAsc(InterviewMessage::getId);
        // List<InterviewMessage> messages = interviewMessageMapper.selectList(wrapper);
        // StringBuilder dialogue = new StringBuilder();
        // for (InterviewMessage msg : messages) {
        // dialogue.append(msg.getRole()) // user / ai
        // .append("（")
        // .append(msg.getAgentCode()) // 哪个Agent说的
        // .append("）：")
        // .append(msg.getContent()) // 内容
        // .append("\n\n");
        // }
        // // 4️⃣ 知识提炼
        // String extractionJson = knowledgeExtractorAgent.extract(dialogue.toString());

        // // 5️⃣ 记忆更新,保存到长期记忆系统中
        // memoryService.saveInterviewExtraction(userId, sessionId, extractionJson);
        // // 6️⃣ 从提炼结果中生成闪卡
        // memoryService.generateFlashcardsFromExtraction(userId, extractionJson);
        // // 更新面试状态
        // InterviewSession session = new InterviewSession();
        // session.setId(sessionId);
        // session.setStatus("FINISHED"); // 标记结束
        // session.setSummary(extractionJson); // 保存总结
        // session.setEndedAt(LocalDateTime.now());
        // interviewSessionMapper.updateById(session);
        // return extractionJson;
        // }

        /**
         * 保存一条面试聊天记录
         *
         * @param sessionId 面试ID
         * @param role      角色：ai / user
         * @param agentCode Agent名称
         * @param content   内容
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

                // MySQL 是最终持久化来源，Redis 是短期缓存。
                // 为避免事务回滚后 Redis 中出现“数据库不存在”的脏消息，缓存写入延后到事务提交成功之后。
                cacheMessageAfterCommit(sessionId, message);

                return message;
        }

        /**
         * 从 Redis 读取最近上下文；未命中时降级查询 MySQL 最近 20 条。
         *
         * @param sessionId 面试ID
         * @return 最近 20 条面试消息，按时间从旧到新排列
         */
        private List<InterviewMessage> getRecentMessagesForPrompt(Long sessionId) {
                List<InterviewMessage> cachedMessages = interviewMemoryCacheService.getRecentMessages(sessionId);
                if (cachedMessages != null && !cachedMessages.isEmpty()) {
                        return cachedMessages;
                }

                return getRecentMessagesFromDatabase(sessionId);
        }

        /**
         * Redis 短期记忆未命中时的兜底查询。
         *
         * 这里只查询最近 20 条，而不是整场面试，避免退化成高成本全量上下文读取。
         */
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

        /**
         * 从一组最近上下文中找到最后一条 AI 消息。
         *
         * @param messages 最近面试上下文
         * @return 最后一条AI消息
         */
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

        /**
         * 事务提交成功后再写 Redis，保证短期记忆不会领先于 MySQL 持久化状态。
         */
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

        /**
         * 等待面试状态和全文提交后再触发异步后处理，确保消费者能读取持久化 transcript。
         */
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
