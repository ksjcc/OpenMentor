package com.service;

import com.dto.dashboard.DashboardDTO;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.FlashcardMapper;
import com.mapper.InterviewSessionMapper;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.UserProfileMapper;
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
  private FlashcardMapper flashcardMapper;

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
    when(flashcardMapper.selectCount(any())).thenReturn(0L);

    DashboardService service = new DashboardService(
        memoryProfileJsonMapper,
        userProfileMapper,
        interviewSessionMapper,
        flashcardMapper,
        new ObjectMapper());

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
    assertThat(dashboard.getAiSuggestions())
        .isNotEmpty()
        .first()
        .asString()
        .contains("Redis缓存一致性");
  }
}
