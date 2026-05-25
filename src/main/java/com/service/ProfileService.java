package com.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ai.agent.ResumeProfileAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import com.dto.profile.ProfileInitRequest;
import com.entity.UserProfile;
import com.mapper.UserProfileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * 用户画像服务
 *
 * 职责：
 * 1. 初始化职业画像
 * 2. 保存结构化画像 JSON
 * 3. 生成长期 Markdown 总结
 * 4. 写入 RAG Chunk
 */
@Service
@RequiredArgsConstructor
public class ProfileService {
    private final ResumeProfileAgent resumeProfileAgent;
    private final PromptBuilder promptBuilder;
    private final UserProfileMapper userProfileMapper;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    public Map<String, String> status() {
        return Map.of("module", "profile", "status", "ready");
    }

    /**
     * 初始化用户画像
     *
     * @param request 初始化请求
     * @return AI解析后的画像JSON
     */
    public String initProfile(ProfileInitRequest request) {
        String agentInput = promptBuilder.buildProfilePrompt(PromptContext.builder()
                .userId(request.getUserId())
                .targetRole(request.getTargetRole())
                .resumeText(request.getResumeText())
                .task(PromptTemplates.TASK_PROFILE)
                .build());

        String profileJson = resumeProfileAgent.parseProfile(agentInput);
        saveOrUpdateUserProfile(request.getUserId(), profileJson);
        memoryService.saveProfileJson(
                request.getUserId(),
                profileJson);
        String mdSummary = buildProfileMarkdown(profileJson);
        memoryService.saveSummaryMd(
                request.getUserId(),
                "overall",
                mdSummary);
        memoryService.saveChunk(
                request.getUserId(),
                "profile",
                request.getUserId(),
                mdSummary);

        return profileJson;
    }

    private void saveOrUpdateUserProfile(Long userId, String profileJson) {

        UserProfile old = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
                        .last("LIMIT 1"));

        try {
            JsonNode root = objectMapper.readTree(profileJson);

            String targetRole = getText(root, "targetRole");
            String experienceLevel = getText(root, "experienceLevel");
            String learningPreference = getText(root, "learningPreference");

            String techStack = root.has("techStack")
                    ? root.get("techStack").toString()
                    : "[]";

            String abilityMap = root.has("abilityMap")
                    ? root.get("abilityMap").toString()
                    : "{}";

            String weaknesses = root.has("weaknesses")
                    ? root.get("weaknesses").toString()
                    : "[]";

            String strengths = root.has("strengths")
                    ? root.get("strengths").toString()
                    : "[]";

            UserProfile profile = old == null ? new UserProfile() : old;

            profile.setUserId(userId);
            profile.setTargetRole(targetRole);
            profile.setExperienceLevel(experienceLevel);
            profile.setTechStack(techStack);
            profile.setAbilityMap(abilityMap);
            profile.setWeaknesses(weaknesses);
            profile.setStrengths(strengths);
            profile.setLearningPreference(learningPreference);

            if (old == null) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }

        } catch (Exception e) {
            throw new RuntimeException("初始化用户画像失败：" + e.getMessage());
        }
    }

    private String buildProfileMarkdown(String profileJson) {

        try {
            JsonNode root = objectMapper.readTree(profileJson);

            return """
                    # 用户长期职业画像

                    ## 目标岗位
                    %s

                    ## 经验水平
                    %s

                    ## 技术栈
                    %s

                    ## 项目经历
                    %s

                    ## 优势
                    %s

                    ## 薄弱点
                    %s

                    ## 学习偏好
                    %s
                    """.formatted(
                    getText(root, "targetRole"),
                    getText(root, "experienceLevel"),
                    root.has("techStack") ? root.get("techStack").toString() : "[]",
                    root.has("projects") ? root.get("projects").toString() : "[]",
                    root.has("strengths") ? root.get("strengths").toString() : "[]",
                    root.has("weaknesses") ? root.get("weaknesses").toString() : "[]",
                    getText(root, "learningPreference"));

        } catch (Exception e) {
            return "# 用户长期职业画像\n\n解析失败，保留原始JSON：\n\n" + profileJson;
        }
    }

    /**
     * 安全读取 JSON 文本字段
     */
    private String getText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText()
                : "";
    }

    /**
     * 获取用户画像
     */
    public UserProfile getProfile(Long userId) {
        return userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
                        .last("LIMIT 1"));
    }
}
