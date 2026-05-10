package com.service;

import com.dto.dashboard.DashboardDTO;
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

    when(userProfileMapper.selectOne(any())).thenReturn(profile);
    when(interviewSessionMapper.selectList(any())).thenReturn(List.of());
    when(flashcardMapper.selectCount(any())).thenReturn(0L);

    DashboardService service = new DashboardService(
        memoryProfileJsonMapper,
        userProfileMapper,
        interviewSessionMapper,
        flashcardMapper,
        new ObjectMapper());

    DashboardDTO dashboard = service.getDashboard(1L);

    assertThat(dashboard.getSkills())
        .extracting("skill")
        .containsExactly("Java基础", "Redis缓存一致性");
    assertThat(dashboard.getWeaknesses())
        .extracting("name")
        .containsExactly("Redis缓存一致性", "JVM GC");
    assertThat(dashboard.getTodayReviewCount()).isZero();
  }
}
