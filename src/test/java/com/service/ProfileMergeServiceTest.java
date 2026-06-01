package com.service;

import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.MemoryDTO;
import com.dto.extraction.StrengthDTO;
import com.dto.extraction.WeaknessDTO;
import com.entity.MemoryProfileJson;
import com.entity.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileMergeServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private MemoryProfileJsonMapper memoryProfileJsonMapper;

  @Mock
  private UserProfileMapper userProfileMapper;

  @Test
  void mergeAndSaveCombinesExtractionAndPersistsBothProfiles() throws Exception {
    MemoryProfileJson oldProfile = new MemoryProfileJson();
    oldProfile.setUserId(1L);
    oldProfile.setVersion(2);
    oldProfile.setProfileData("""
        {
          "targetRole": "Java后端开发",
          "experienceLevel": "初级",
          "techStack": ["Java", "Redis"],
          "strengths": [{"name": "Java基础", "evidence": "简历项目", "occurrenceCount": 1}],
          "weaknesses": [{"name": "JVM GC", "level": 2, "evidence": "初始画像", "occurrenceCount": 1}],
          "abilityMap": {"Java基础": 60, "JVM GC": 50, "Redis缓存一致性": 55},
          "learningPreference": "项目驱动"
        }
        """);

    when(memoryProfileJsonMapper.selectOne(any())).thenReturn(oldProfile);
    when(userProfileMapper.selectOne(any())).thenReturn(null);

    KnowledgeExtractionResult extraction = new KnowledgeExtractionResult();

    StrengthDTO strength = new StrengthDTO();
    strength.setName("Java基础");
    strength.setEvidence("回答清楚集合和并发基础");

    WeaknessDTO weakness = new WeaknessDTO();
    weakness.setName("Redis缓存一致性");
    weakness.setLevel(3);
    weakness.setEvidence("延迟双删边界条件不完整");

    MemoryDTO memory = new MemoryDTO();
    memory.setProfileUpdate("""
        {
          "abilityMap": {
            "Spring Boot": 65
          }
        }
        """);

    extraction.setStrengths(List.of(strength));
    extraction.setWeaknesses(List.of(weakness));
    extraction.setMemory(memory);

    ProfileMergeService service = new ProfileMergeService(
        memoryProfileJsonMapper,
        userProfileMapper,
        objectMapper);

    String mergedProfileJson = service.mergeAndSave(1L, extraction);
    JsonNode merged = objectMapper.readTree(mergedProfileJson);

    assertThat(merged.path("abilityMap").path("Java基础").asInt()).isEqualTo(70);
    assertThat(merged.path("abilityMap").path("Redis缓存一致性").asInt()).isEqualTo(25);
    assertThat(merged.path("abilityMap").path("Spring Boot").asInt()).isEqualTo(53);
    assertThat(merged.path("strengths").get(0).path("occurrenceCount").asInt()).isEqualTo(2);
    assertThat(merged.path("weaknesses").get(1).path("name").asText()).isEqualTo("Redis缓存一致性");
    assertThat(merged.path("weaknesses").get(1).path("level").asInt()).isEqualTo(3);

    ArgumentCaptor<MemoryProfileJson> memoryCaptor = ArgumentCaptor.forClass(MemoryProfileJson.class);
    verify(memoryProfileJsonMapper).insert(memoryCaptor.capture());
    assertThat(memoryCaptor.getValue().getUserId()).isEqualTo(1L);
    assertThat(memoryCaptor.getValue().getVersion()).isEqualTo(3);
    assertThat(objectMapper.readTree(memoryCaptor.getValue().getProfileData()).path("abilityMap").path("Spring Boot").asInt())
        .isEqualTo(53);

    ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
    verify(userProfileMapper).insert(profileCaptor.capture());
    assertThat(profileCaptor.getValue().getUserId()).isEqualTo(1L);
    assertThat(profileCaptor.getValue().getTargetRole()).isEqualTo("Java后端开发");
    assertThat(profileCaptor.getValue().getAbilityMap()).contains("Spring Boot");
    assertThat(profileCaptor.getValue().getWeaknesses()).contains("Redis缓存一致性");
    assertThat(profileCaptor.getValue().getStrengths()).contains("Java基础");
  }
}
