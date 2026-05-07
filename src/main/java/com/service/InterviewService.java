package com.service;

import com.common.BizException;
import com.common.ErrorCode;
import com.ai.agent.TechInterviewerAgent;
import com.ai.orchestrator.AgentOrchestrator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.InterviewReplyDTO;
import com.entity.InterviewMessage;
import com.entity.InterviewSession;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.ScoreAgent;
import com.mapper.InterviewMessageMapper;
import com.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        public InterviewReplyDTO startInterview(Long userId, String interviewType, String targetRole, String topic) {
                validateStartRequest(userId, interviewType, targetRole, topic);

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
                String question = agentOrchestrator.generateFirstQuestion(userId, topic);
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
                assertSessionInProgress(userId, sessionId);

                // 1. 保存用户回答
                saveMessage(
                                sessionId,
                                ROLE_USER,
                                "User",
                                answer);

                // 2. 获取上一条 AI 问题
                // 因为评分需要知道：用户是在回答哪个问题

                InterviewMessage lastAiQuestion = getLastAiMessage(sessionId);

                String question = lastAiQuestion == null
                                ? "未知问题"
                                : lastAiQuestion.getContent();

                // 3. 调用 AgentOrchestrator：
                // 评分 → 决策 → 生成追问/下一题
                InterviewReplyDTO reply = agentOrchestrator.HandleAnswer(
                                userId,
                                topic,
                                question,
                                answer);
                reply.setSessionId(sessionId);
                // 4. 保存 AI 新问题 / 追问
                if (reply == null || isBlank(reply.getContent())) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成下一步回复");
                }
                if (isBlank(reply.getAction())) {
                        reply.setAction(ACTION_NEXT);
                }

                saveMessage(
                                sessionId,
                                ROLE_AI,
                                ACTION_FOLLOW_UP.equals(reply.getAction())
                                                ? "FollowUpAgent"
                                                : "TechInterviewerAgent",
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
                String extractionJson = agentOrchestrator.extractKnowledge(dialogue.toString());
                if (isBlank(extractionJson)) {
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "AI未生成知识提炼结果");
                }

                // 4. 写入长期记忆
                // JSON / MD / Chunk 三层记忆
                memoryService.saveInterviewExtraction(
                                userId,
                                sessionId,
                                extractionJson);

                // 5. 从提炼结果中生成闪卡
                generateFlashcardsSafely(userId, extractionJson);

                // 6. 更新面试 session 状态
                InterviewSession session = new InterviewSession();
                session.setId(sessionId);
                session.setStatus(STATUS_FINISHED);
                session.setSummary(extractionJson);
                session.setEndedAt(LocalDateTime.now());

                interviewSessionMapper.updateById(session);

                return extractionJson;
        }

        /**
         * 用户回答后处理
         */
        public String handleAnswer(Long userId, String topic, String answer) {
                validateRequired(userId, "用户ID不能为空");
                validateText(topic, "面试主题不能为空");
                validateText(answer, "回答内容不能为空");

                String scoreInput = """
                                问题主题：%s

                                用户回答：
                                %s
                                """.formatted(topic, answer);

                String scoreResult = scoreAgent.score(scoreInput);

                // 2️⃣ 决策
                String decisionInput = """
                                评分结果：
                                %s
                                """.formatted(scoreResult);

                String decision = followUpAgent.decide(decisionInput);

                if (decision.contains("FOLLOW_UP")) {
                        return "继续追问：" + interviewerAgent.askQuestion(answer);
                }

                if (decision.contains("HARDER")) {
                        return "提高难度：" + interviewerAgent.askQuestion(topic + " 高级问题");
                }

                return "下一题：" + interviewerAgent.askQuestion(topic);
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
        public void saveMessage(Long sessionId, String role, String agentCode, String content) {
                validateRequired(sessionId, "面试ID不能为空");
                validateText(role, "消息角色不能为空");
                validateText(content, "消息内容不能为空");

                InterviewMessage message = new InterviewMessage();
                message.setSessionId(sessionId);
                message.setRole(role);
                message.setAgentCode(agentCode);
                message.setContent(content);
                interviewMessageMapper.insert(message);
        }

        /**
         * 获取本场面试最后一条 AI 问题
         *
         * @param sessionId 面试ID
         * @return 最后一条AI消息
         */
        private InterviewMessage getLastAiMessage(Long sessionId) {
                return interviewMessageMapper.selectOne(
                                new LambdaQueryWrapper<InterviewMessage>()
                                                .eq(InterviewMessage::getSessionId, sessionId)
                                                .eq(InterviewMessage::getRole, "ai")
                                                .orderByDesc(InterviewMessage::getId)
                                                .last("LIMIT 1"));
        }

        private InterviewSession assertSessionInProgress(Long userId, Long sessionId) {
                InterviewSession session = interviewSessionMapper.selectById(sessionId);
                if (session == null) {
                        throw new BizException(ErrorCode.NOT_FOUND, "面试不存在");
                }
                if (!userId.equals(session.getUserId())) {
                        throw new BizException(ErrorCode.UNAUTHORIZED, "无权访问该面试");
                }
                if (STATUS_FINISHED.equals(session.getStatus())) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "面试已结束");
                }
                return session;
        }

        private void validateStartRequest(Long userId, String interviewType, String targetRole, String topic) {
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

        private void generateFlashcardsSafely(Long userId, String extractionJson) {
                try {
                        flashcardService.generateFlashcardsFromExtraction(userId, extractionJson);
                } catch (Exception e) {
                        System.err.println("生成闪卡失败：" + e.getMessage());
                }
        }

}
