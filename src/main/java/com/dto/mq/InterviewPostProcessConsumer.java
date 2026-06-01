package com.dto.mq;

import com.common.RabbitMQConstants;
import com.dto.extraction.KnowledgeExtractionResult;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.mapper.InterviewSessionMapper;
import com.service.*;
import com.service.Dashboard.FlashcardService;
import com.service.garphrag.GraphCommunityService;
import com.service.garphrag.GraphRagIndexerService;
import com.dto.graph.KnowledgeGraphBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 面试后处理消费者
 *
 * 监听 RabbitMQ 队列，异步完成：
 * 1. JSON解析
 * 2. 用户画像合并
 * 3. 长期记忆更新
 * 4. 闪卡生成
 * 5. 知识图谱构建
 * 6. 整场面试上下文写入 GraphRAG 索引
 */
@Component
@RequiredArgsConstructor
public class InterviewPostProcessConsumer {
  private final JsonParseService jsonParseService;
  private final ProfileMergeService profileMergeService;
  private final MemoryService memoryService;
  private final FlashcardService flashcardService;
  private final KnowledgeGraphBuilder knowledgeGraphBuilder;
  private final GraphRagIndexerService graphRagIndexerService;
  private final GraphCommunityService graphCommunityService;
  private final InterviewSessionMapper interviewSessionMapper;
  private final ProfileService profileService;
  private final RabbitTemplate rabbitTemplate;

  private static final int MAX_RETRY_COUNT = 3;
  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_PROCESSING = "PROCESSING";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILED = "FAILED";

  /**
   * 监听面试后处理队列
   *
   * @RabbitListener 会自动从队列中取消息并调用该方法。
   */
  @RabbitListener(queues = RabbitMQConstants.INTERVIEW_POST_PROCESS_QUEUE)
  public void consume(InterviewPostProcessMessage message) {
    Long userId = message.getUserId();
    Long sessionId = message.getSessionId();
    InterviewSession session = interviewSessionMapper.selectById(sessionId);
    if (session == null || STATUS_SUCCESS.equals(session.getPostProcessStatus())) {
      return;
    }

    markProcessing(session);
    try {
      String extractionJson = message.getExtractionJson();
      KnowledgeExtractionResult extraction = jsonParseService.parseExtraction(extractionJson);
      String resumeProjectText = loadResumeProjectText(userId);
      profileMergeService.mergeAndSave(userId, extraction);
      memoryService.updateMemoryFromExtraction(userId, sessionId, extraction);
      flashcardService.generateFlashcardsFromExtraction(userId, extraction);
      knowledgeGraphBuilder.buildGraph(userId, extraction);
      graphRagIndexerService.indexText(
          userId,
          "INTERVIEW",
          sessionId,
          buildGraphRagContent(session, extraction, resumeProjectText));
      graphCommunityService.rebuildCommunities(userId);
      markSuccess(sessionId);
    } catch (Exception e) {
      handleFailure(message, session, e);
    }
  }

  private void markProcessing(InterviewSession session) {
    InterviewSession update = new InterviewSession();
    update.setId(session.getId());
    update.setPostProcessStatus(STATUS_PROCESSING);
    update.setPostProcessError(null);
    update.setPostProcessFinishedAt(null);
    interviewSessionMapper.updateById(update);
  }

  private void markSuccess(Long sessionId) {
    InterviewSession update = new InterviewSession();
    update.setId(sessionId);
    update.setPostProcessStatus(STATUS_SUCCESS);
    update.setPostProcessError(null);
    update.setPostProcessFinishedAt(LocalDateTime.now());
    interviewSessionMapper.updateById(update);
  }

  private void handleFailure(
      InterviewPostProcessMessage message,
      InterviewSession session,
      Exception e) {
    int retryCount = safeRetryCount(session.getPostProcessRetryCount()) + 1;
    markFailed(session.getId(), retryCount, e);

    if (retryCount < MAX_RETRY_COUNT) {
      rabbitTemplate.convertAndSend(
          RabbitMQConstants.INTERVIEW_EXCHANGE,
          RabbitMQConstants.INTERVIEW_POST_PROCESS_ROUTING_KEY,
          message);
      return;
    }

    rabbitTemplate.convertAndSend(
        RabbitMQConstants.INTERVIEW_EXCHANGE,
        RabbitMQConstants.INTERVIEW_POST_PROCESS_DLQ_ROUTING_KEY,
        message);
    throw new AmqpRejectAndDontRequeueException(
        "面试后处理失败超过最大重试次数，消息已转入死信队列", e);
  }

  private void markFailed(Long sessionId, int retryCount, Exception e) {
    InterviewSession update = new InterviewSession();
    update.setId(sessionId);
    update.setPostProcessStatus(STATUS_FAILED);
    update.setPostProcessRetryCount(retryCount);
    update.setPostProcessError(truncateError(e));
    update.setPostProcessFinishedAt(LocalDateTime.now());
    interviewSessionMapper.updateById(update);
  }

  private int safeRetryCount(Integer retryCount) {
    return retryCount == null ? 0 : retryCount;
  }

  private String truncateError(Exception e) {
    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }

  private String loadResumeProjectText(Long userId) {
    UserProfile profile = profileService.getProfile(userId);
    if (profile != null && !isBlank(profile.getResumeText())) {
      return profile.getResumeText();
    }

    // Existing profiles created before resume_text was introduced can still
    // contribute their structured project/profile JSON to the index.
    return memoryService.getLastProfileJson(userId);
  }

  private String buildGraphRagContent(
      InterviewSession session,
      KnowledgeExtractionResult extraction,
      String resumeProjectText) {
    StringBuilder content = new StringBuilder();
    appendSection(content, "完整面试对话", session == null ? null : session.getTranscript());
    appendSection(content, "AI 总结", extraction == null ? null : extraction.getSummary());

    if (extraction != null && extraction.getMemory() != null
        && extraction.getMemory().getChunks() != null) {
      StringBuilder chunks = new StringBuilder();
      for (String chunk : extraction.getMemory().getChunks()) {
        if (!isBlank(chunk)) {
          chunks.append("- ").append(chunk.trim()).append("\n");
        }
      }
      appendSection(content, "Memory Chunks", chunks.toString());
    }

    appendSection(content, "用户简历/项目文本", resumeProjectText);
    return content.toString();
  }

  private void appendSection(StringBuilder content, String title, String text) {
    if (isBlank(text)) {
      return;
    }
    content.append("## ")
        .append(title)
        .append("\n")
        .append(text.trim())
        .append("\n\n");
  }

  private boolean isBlank(String text) {
    return text == null || text.isBlank();
  }

}
