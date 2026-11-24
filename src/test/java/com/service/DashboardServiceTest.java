package com.service;

import com.dto.dashboard.DashboardDTO;
import com.dto.dashboard.LearningRecommendationDTO;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.WeaknessDTO;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.FlashcardMapper;
import com.mapper.InterviewMessageMapper;
import com.mapper.InterviewSessionMapper;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.UserProfileMapper;
import com.service.Dashboard.LearningRecommendationService;
import com.service.Dashboard.DashboardService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock
  private MemoryProfileJsonMapper memoryProfileJsonMapper;

  @Mock
  private UserProfileMapper userProfileMapper;

  @Mock
  private InterviewSessionMapper interviewSessionMapper;

  @Mock
  private InterviewMessageMapper interviewMessageMapper;

  @Mock
  private FlashcardMapper flashcardMapper;

  @Mock
  private LearningRecommendationService learningRecommendationService;

  @Mock
  private JsonParseService jsonParseService;

  @Test
  void getDashboardUsesMergedUserProfileForSkillsAndWeaknesses() {
    UserProfile profile = new UserProfile();
    profile.setUserId(1L);
    profile.setAbilityMap("{\"Java基础\":70,\"Redis缓存一致性\":25}");
    profile.setWeaknesses("""
        [
          {"name": "JVM GC", "occurrenceCount": 1},
          {"name": "Redis缓存一致性", "occurrenceCount": 3}
        ]
        """);

    InterviewSession session = new InterviewSession();
    session.setId(10L);
    session.setStatus("FINISHED");
    session.setTargetRole("Java后端开发");
    session.setTopic("Redis 持久化");
    session.setScore(82);
    session.setSummary("""
        {
          "summary": "本次面试暴露出 Redis 一致性和 JVM GC 的短板",
          "weaknesses": [
            {"name": "JVM GC", "level": 2, "evidence": "对回收过程解释不完整"},
            {"name": "Redis缓存一致性", "level": 4, "evidence": "缺少延迟双删与旁路缓存比较"}
          ]
        }
        """);

    when(userProfileMapper.selectOne(any())).thenReturn(profile);
    when(interviewSessionMapper.selectList(any())).thenReturn(List.of(session));
    when(interviewSessionMapper.selectCount(any())).thenReturn(3L);
    when(flashcardMapper.selectCount(any())).thenReturn(0L);
    WeaknessDTO jvmWeakness = new WeaknessDTO();
    jvmWeakness.setName("JVM GC");
    jvmWeakness.setLevel(2);
    WeaknessDTO redisWeakness = new WeaknessDTO();
    redisWeakness.setName("Redis缓存一致性");
    redisWeakness.setLevel(4);
    KnowledgeExtractionResult extraction = new KnowledgeExtractionResult();
    extraction.setWeaknesses(List.of(jvmWeakness, redisWeakness));
    when(jsonParseService.parseExtractionLenient(any())).thenReturn(extraction);
    when(learningRecommendationService.generate(1L)).thenReturn(LearningRecommendationDTO.builder()
        .weakTopics(List.of("Redis缓存一致性", "JVM GC"))
        .roleGapSuggestions(List.of("建议优先补强 Redis缓存一致性 的设计取舍表达。"))
        .flashcards(List.of())
        .reviewOrder(List.of("Redis缓存一致性", "JVM GC"))
        .build());

    DashboardService service = new DashboardService(
        memoryProfileJsonMapper,
        userProfileMapper,
        interviewSessionMapper,
        interviewMessageMapper,
        flashcardMapper,
        learningRecommendationService,
        new ObjectMapper(),
        jsonParseService);

    DashboardDTO dashboard = service.getDashboard(1L);

    assertThat(dashboard.getSkills())
        .extracting("skill", "score")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Java基础", 70),
            org.assertj.core.groups.Tuple.tuple("Redis缓存一致性", 0));
    assertThat(dashboard.getWeaknesses())
        .extracting("name", "count")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Redis缓存一致性", 7),
            org.assertj.core.groups.Tuple.tuple("JVM GC", 3));
    assertThat(dashboard.getTodayReviewCount()).isZero();
    assertThat(dashboard.getTotalSessions()).isEqualTo(3);
    assertThat(dashboard.getAverageScore()).isEqualTo(82);
    assertThat(dashboard.getScoreTrend()).containsExactly(82);
    assertThat(dashboard.getRecentInterviews()).hasSize(1);
    assertThat(dashboard.getRecentInterviews().get(0).getTopic()).isEqualTo("Redis 持久化");
    assertThat(dashboard.getAiSuggestions())
        .isNotEmpty()
        .first()
        .asString()
        .contains("Redis缓存一致性");
  }
}
