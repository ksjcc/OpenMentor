package com.controller;

import com.dto.InterviewReplyDTO;
import com.dto.dashboard.DashboardDTO;
import com.dto.graph.GraphDTO;
import com.dto.graph.GraphLinkDTO;
import com.dto.graph.GraphNodeDTO;
import com.entity.Flashcard;
import com.entity.MemoryChunk;
import com.service.InterviewService;
import com.service.KnowledgeQaService;
import com.service.KnowledgeGraphService;
import com.service.MemoryService;
import com.service.ReviewService;
import com.service.Dashboard.DashboardService;
import com.service.Dashboard.FlashcardService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiSmokeTest {

  private MockMvc mockMvc;

  @Mock
  private InterviewService interviewService;

  @Mock
  private FlashcardService flashcardService;

  @Mock
  private ReviewService reviewService;

  @Mock
  private DashboardService dashboardService;

  @Mock
  private KnowledgeGraphService knowledgeGraphService;

  @Mock
  private MemoryService memoryService;

  @Mock
  private KnowledgeQaService knowledgeQaService;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(
        new InterviewController(interviewService),
        new KnowledgeController(knowledgeQaService),
        new ReviewController(reviewService, flashcardService),
        new DashboardController(dashboardService),
        new KnowledgeGraphController(knowledgeGraphService),
        new MemoryController(memoryService))
        .build();
  }

  @Test
  void startInterviewRunsThrough() throws Exception {
    InterviewReplyDTO reply = new InterviewReplyDTO();
    reply.setSessionId(10L);
    reply.setAction("QUESTION");
    reply.setContent("Redis 如何保证缓存一致性？");
    when(interviewService.startInterview(1L, "TECH", "Java后端开发", "Redis", null))
        .thenReturn(reply);

    mockMvc.perform(post("/api/interview/start")
        .param("userId", "1")
        .param("interviewType", "TECH")
        .param("targetRole", "Java后端开发")
        .param("topic", "Redis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.sessionId").value(10))
        .andExpect(jsonPath("$.data.action").value("QUESTION"));
  }

  @Test
  void answerInterviewRunsThrough() throws Exception {
    InterviewReplyDTO reply = new InterviewReplyDTO();
    reply.setSessionId(10L);
    reply.setAction("FOLLOW_UP");
    reply.setContent("继续说明延迟双删的缺点。");
    when(interviewService.answerQuestion(1L, 10L, "Redis", "cache answer"))
        .thenReturn(reply);

    mockMvc.perform(post("/api/interview/{sessionId}/answer", 10L)
        .param("userId", "1")
        .param("topic", "Redis")
        .content("cache answer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.sessionId").value(10))
        .andExpect(jsonPath("$.data.action").value("FOLLOW_UP"));
  }

  @Test
  void finishInterviewRunsThrough() throws Exception {
    when(interviewService.finishInterview(1L, 10L))
        .thenReturn("{\"summary\":\"done\"}");

    mockMvc.perform(post("/api/interview/{sessionId}/finish", 10L)
        .param("userId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("{\"summary\":\"done\"}"));
  }

  @Test
  void todayReviewRunsThrough() throws Exception {
    Flashcard card = new Flashcard();
    card.setId(100L);
    card.setUserId(1L);
    card.setQuestion("Redis 缓存穿透是什么？");
    card.setAnswer("查询不存在的数据导致请求打到数据库。");
    when(flashcardService.getTodayReview(1L)).thenReturn(List.of(card));

    mockMvc.perform(get("/api/review/today")
        .param("userId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[0].id").value(100))
        .andExpect(jsonPath("$.data[0].question").value("Redis 缓存穿透是什么？"));
  }

  @Test
  void submitReviewRunsThrough() throws Exception {
    mockMvc.perform(post("/api/review/submit")
        .param("cardId", "100")
        .param("correct", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.message").value("提交成功"));

    verify(flashcardService).review(100L, true);
  }

  @Test
  void dashboardRunsThrough() throws Exception {
    DashboardDTO dashboard = new DashboardDTO();
    dashboard.setTodayReviewCount(3);
    dashboard.setAiSuggestions(List.of("建议加强 Redis 缓存一致性。"));
    when(dashboardService.getDashboard(1L)).thenReturn(dashboard);

    mockMvc.perform(get("/api/dashboard")
        .param("userId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.todayReviewCount").value(3))
        .andExpect(jsonPath("$.data.aiSuggestions[0]").value("建议加强 Redis 缓存一致性。"));
  }

  @Test
  void graphRunsThrough() throws Exception {
    GraphNodeDTO node = new GraphNodeDTO();
    node.setId(1L);
    node.setName("Redis");
    node.setType("TECH");
    node.setMasteryLevel(70);
    node.setImportanceScore(90);

    GraphLinkDTO link = new GraphLinkDTO();
    link.setSource(1L);
    link.setTarget(2L);
    link.setRelationType("RELATED");
    link.setWeight(0.7);

    GraphDTO graph = new GraphDTO();
    graph.setNodes(List.of(node));
    graph.setLinks(List.of(link));
    when(knowledgeGraphService.getGraph(1L)).thenReturn(graph);

    mockMvc.perform(get("/api/graph")
        .param("userId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.nodes[0].name").value("Redis"))
        .andExpect(jsonPath("$.data.links[0].relationType").value("RELATED"));
  }

  @Test
  void knowledgeAskRunsThrough() throws Exception {
    when(knowledgeQaService.answer(org.mockito.ArgumentMatchers.eq(1L), anyString()))
        .thenReturn("Redis 快主要因为内存访问、单线程事件循环和高效数据结构。");

    mockMvc.perform(post("/api/knowledge/ask")
        .param("userId", "1")
        .contentType(TEXT_PLAIN)
        .content("Redis 为什么快？"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("Redis 快主要因为内存访问、单线程事件循环和高效数据结构。"));
  }

  @Test
  void memorySearchRunsThrough() throws Exception {
    MemoryChunk chunk = new MemoryChunk();
    chunk.setId(200L);
    chunk.setUserId(1L);
    chunk.setSourceType("interview");
    chunk.setSourceId(10L);
    chunk.setContent("Redis 延迟双删需要考虑并发读写。");
    when(memoryService.searchRelevantChunks(1L, "Redis", 5)).thenReturn(List.of(chunk));

    mockMvc.perform(get("/api/memory/search")
        .param("userId", "1")
        .param("keyword", "Redis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[0].id").value(200))
        .andExpect(jsonPath("$.data[0].content").value("Redis 延迟双删需要考虑并发读写。"));
  }
}
