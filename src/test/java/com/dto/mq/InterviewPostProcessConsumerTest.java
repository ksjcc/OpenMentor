package com.dto.mq;

import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.MemoryDTO;
import com.dto.graph.KnowledgeGraphBuilder;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.mapper.InterviewSessionMapper;
import com.service.JsonParseService;
import com.service.MemoryService;
import com.service.ProfileMergeService;
import com.service.ProfileService;
import com.service.Dashboard.FlashcardService;
import com.service.garphrag.GraphCommunityService;
import com.service.garphrag.GraphRagIndexerService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewPostProcessConsumerTest {

  @Mock
  private JsonParseService jsonParseService;
  @Mock
  private ProfileMergeService profileMergeService;
  @Mock
  private MemoryService memoryService;
  @Mock
  private FlashcardService flashcardService;
  @Mock
  private KnowledgeGraphBuilder knowledgeGraphBuilder;
  @Mock
  private GraphRagIndexerService graphRagIndexerService;
  @Mock
  private GraphCommunityService graphCommunityService;
  @Mock
  private InterviewSessionMapper interviewSessionMapper;
  @Mock
  private ProfileService profileService;
  @Mock
  private RabbitTemplate rabbitTemplate;

  @Test
  void indexesTranscriptSummaryMemoryAndResumeProjectTextTogether() {
    InterviewPostProcessMessage message = new InterviewPostProcessMessage();
    message.setUserId(1L);
    message.setSessionId(5L);
    message.setExtractionJson("{\"summary\":\"summary\"}");

    InterviewSession session = new InterviewSession();
    session.setTranscript("ai: Java 中的锁是什么？\nuser: 用于并发控制。");
    session.setPostProcessStatus("PENDING");
    when(interviewSessionMapper.selectById(5L)).thenReturn(session);

    UserProfile profile = new UserProfile();
    profile.setResumeText("参与订单系统项目，使用 Redis 与 MySQL。");
    when(profileService.getProfile(1L)).thenReturn(profile);

    MemoryDTO memory = new MemoryDTO();
    memory.setChunks(List.of("并发控制基础较好", "需要补充锁升级机制"));
    KnowledgeExtractionResult extraction = new KnowledgeExtractionResult();
    extraction.setSummary("候选人能描述并发控制用途。");
    extraction.setMemory(memory);
    when(jsonParseService.parseExtraction(message.getExtractionJson())).thenReturn(extraction);

    consumer().consume(message);

    ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
    verify(graphRagIndexerService).indexText(
        org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq("INTERVIEW"),
        org.mockito.ArgumentMatchers.eq(5L),
        content.capture());
    assertThat(content.getValue())
        .contains("完整面试对话", session.getTranscript())
        .contains("AI 总结", extraction.getSummary())
        .contains("Memory Chunks", "并发控制基础较好", "需要补充锁升级机制")
        .contains("用户简历/项目文本", profile.getResumeText());
  }

  private InterviewPostProcessConsumer consumer() {
    return new InterviewPostProcessConsumer(
        jsonParseService,
        profileMergeService,
        memoryService,
        flashcardService,
        knowledgeGraphBuilder,
        graphRagIndexerService,
        graphCommunityService,
        interviewSessionMapper,
        profileService,
        rabbitTemplate);
  }
}
