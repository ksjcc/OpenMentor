package com.service.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.agent.ResumeProfileAgent;
import com.ai.prompt.PromptBuilder;
import com.entity.Resume;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.ResumeMapper;
import com.service.MemoryService;
import com.service.ProfileService;

/**
 * 简历解析落库的边界：模型不可用时 agent 层会返回一份"空壳"画像，
 * 不能把它当成解析成功，否则用户看到的是"已解析"但内容全空。
 */
@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

  private static final String CONTENT = "5 年 Java 后端经验，负责订单中台与支付网关，主导 Redis 集群迁移。";
  private static final String EMPTY_SHELL_PROFILE = """
      {"targetRole":"","experienceLevel":"","techStack":[],"projects":[],"strengths":[],
       "weaknesses":[],"abilityMap":{},"learningPreference":"fallback"}
      """;
  private static final String MEANINGFUL_PROFILE = """
      {"targetRole":"Java 后端开发","experienceLevel":"中级","techStack":["Java","Redis"],
       "projects":[{"name":"订单中台"}],"strengths":[],"weaknesses":[],
       "abilityMap":{"Java":70,"Redis":60},"learningPreference":"动手实践"}
      """;

  @Mock
  private ResumeMapper resumeMapper;

  @Mock
  private ResumeProfileAgent resumeProfileAgent;

  @Mock
  private PromptBuilder promptBuilder;

  @Mock
  private MemoryService memoryService;

  @Mock
  private ProfileService profileService;

  @Mock
  private ResumeTextExtractor resumeTextExtractor;

  private ResumeService service;

  @BeforeEach
  void setUp() {
    service = new ResumeService(
        resumeMapper,
        resumeProfileAgent,
        promptBuilder,
        memoryService,
        profileService,
        resumeTextExtractor,
        new ObjectMapper());
  }

  @Test
  void createKeepsResumeButSkipsEmptyShellProfile() {
    stubInsertAsNewResume();
    when(promptBuilder.buildProfilePrompt(any())).thenReturn("PROMPT");
    when(resumeProfileAgent.parseProfile("PROMPT")).thenReturn(EMPTY_SHELL_PROFILE);

    Resume created = service.createFromText(1L, "我的简历", CONTENT, "Java 后端开发", true);

    assertThat(created.getId()).isEqualTo(7L);
    assertThat(created.getParsedJson()).isNull();
    // 解析失败不应该写 parsedJson
    verify(resumeMapper, never()).updateById(any(Resume.class));
  }

  @Test
  void createStoresParsedJsonWhenModelReturnsMeaningfulProfile() {
    stubInsertAsNewResume();
    when(promptBuilder.buildProfilePrompt(any())).thenReturn("PROMPT");
    when(resumeProfileAgent.parseProfile("PROMPT")).thenReturn(MEANINGFUL_PROFILE);

    Resume created = service.createFromText(1L, "我的简历", CONTENT, "Java 后端开发", true);

    assertThat(created.getParsedJson()).isNotNull().contains("Java 后端开发");
    verify(resumeMapper).updateById(any(Resume.class));
    // 第一份简历自动成为默认，并同步进画像（只同步一次，解析成功不再重复写）
    verify(profileService, times(1)).syncResumeText(eq(1L), eq(CONTENT));
    assertThat(created.getIsDefault()).isEqualTo(1);
  }

  @Test
  void createWithoutParseDoesNotCallModel() {
    stubInsertAsNewResume();

    Resume created = service.createFromText(1L, "我的简历", CONTENT, null, false);

    assertThat(created.getParsedJson()).isNull();
    verify(resumeProfileAgent, never()).parseProfile(anyString());
  }

  private void stubInsertAsNewResume() {
    when(resumeMapper.selectCount(any())).thenReturn(0L);
    when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
      Resume resume = invocation.getArgument(0);
      resume.setId(7L);
      return 1;
    });
  }
}
