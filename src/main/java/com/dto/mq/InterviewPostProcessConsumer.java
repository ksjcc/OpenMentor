package com.dto.mq;

import com.common.RabbitMQConstants;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.mq.InterviewPostProcessMessage;
import com.service.*;
import com.service.garphrag.GraphCommunityService;
import com.service.garphrag.GraphRagIndexerService;
import com.dto.graph.KnowledgeGraphBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 面试后处理消费者
 *
 * 监听 RabbitMQ 队列，异步完成：
 * 1. JSON解析
 * 2. 用户画像合并
 * 3. 长期记忆更新
 * 4. 闪卡生成
 * 5. 知识图谱构建
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

  /**
   * 监听面试后处理队列
   *
   * @RabbitListener 会自动从队列中取消息并调用该方法。
   */
  @RabbitListener(queues = RabbitMQConstants.INTERVIEW_POST_PROCESS_QUEUE)
  public void consume(InterviewPostProcessMessage message) {
    Long userId = message.getUserId();
    Long sessionId = message.getSessionId();
    String extractionJson = message.getExtractionJson();
    KnowledgeExtractionResult extraction = jsonParseService.parseExtraction(extractionJson);
    profileMergeService.mergeAndSave(userId, extraction);
    memoryService.updateMemoryFromExtraction(userId, sessionId, extraction);
    flashcardService.generateFlashcardsFromExtraction(userId, extraction);
    knowledgeGraphBuilder.buildGraph(userId, extraction);
    graphRagIndexerService.indexText(message.getUserId(),
        "interview_summary",
        message.getSessionId(),
        extraction.getSummary());
    graphCommunityService.rebuildCommunities(
        message.getUserId());
  }

}
