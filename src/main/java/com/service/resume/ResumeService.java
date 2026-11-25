package com.service.resume;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.agent.ResumeProfileAgent;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.BizException;
import com.common.ErrorCode;
import com.entity.Resume;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.ResumeMapper;
import com.service.MemoryService;
import com.service.ProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 简历库。
 *
 * <p>
 * 一个用户可以有多份简历，创建面试时选择其中一份注入面试官上下文（见
 * {@code InterviewService}）。简历正文会同时写入长期记忆（sourceType=resume），
 * 这样面试官用 retrieve_material 的 PREFERENCE 层也能召回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final int MEMORY_CHUNK_MAX = 4000;
    private static final int PARSED_PREVIEW_MAX = 1500;
    private static final int TITLE_MAX = 80;
    private static final int DISPLAY_CONTENT_MAX = 300;

    private final ResumeMapper resumeMapper;
    private final ResumeProfileAgent resumeProfileAgent;
    private final PromptBuilder promptBuilder;
    private final MemoryService memoryService;
    private final ProfileService profileService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ObjectMapper objectMapper;

    // ==================== 查询 ====================

    public List<Resume> list(Long userId) {
        requireUser(userId);
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getIsDefault)
                .orderByDesc(Resume::getUpdatedAt)
                .orderByDesc(Resume::getId));
    }

    /**
     * 列表展示用：不带简历全文与解析 JSON，避免一次拉几百 KB。
     */
    public List<Resume> listForDisplay(Long userId) {
        return list(userId).stream().map(this::toDisplayCopy).toList();
    }

    private Resume toDisplayCopy(Resume source) {
        Resume copy = new Resume();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setTitle(source.getTitle());
        copy.setTargetRole(source.getTargetRole());
        copy.setFileName(source.getFileName());
        copy.setFileType(source.getFileType());
        copy.setIsDefault(source.getIsDefault());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setHasParsed(!isBlank(source.getParsedJson()));
        String content = source.getContent() == null ? "" : source.getContent();
        copy.setContent(content.length() > DISPLAY_CONTENT_MAX ? content.substring(0, DISPLAY_CONTENT_MAX) : content);
        return copy;
    }

    public Resume get(Long userId, Long resumeId) {
        requireUser(userId);
        if (resumeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历ID不能为空");
        }
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "简历不存在");
        }
        if (!userId.equals(resume.getUserId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权访问该简历");
        }
        return resume;
    }

    /** 默认简历：没有显式默认时退化为最近更新的一份。 */
    public Resume findDefault(Long userId) {
        List<Resume> resumes = list(userId);
        if (resumes.isEmpty()) {
            return null;
        }
        return resumes.stream()
                .filter(item -> item.getIsDefault() != null && item.getIsDefault() == 1)
                .findFirst()
                .orElse(resumes.get(0));
    }

    // ==================== 写入 ====================

    /**
     * 文本新建。
     *
     * <p>
     * 刻意不加 {@code @Transactional}：parse=true 时这里会调用一次模型（可能几十秒），
     * 把模型调用放进数据库事务会长时间占着连接。插入本身是单条语句，够原子。
     */
    public Resume createFromText(Long userId, String title, String content, String targetRole, boolean parse) {
        requireUser(userId);
        String text = requireContent(content);
        Resume resume = doCreate(userId, title, text, targetRole, null, "TEXT");
        if (parse) {
            tryParse(userId, resume, false);
        }
        return resume;
    }

    /** 文件上传新建，同上：模型解析在事务外。 */
    public Resume createFromFile(Long userId, String title, String fileName, byte[] bytes, String targetRole,
            boolean parse) {
        requireUser(userId);
        ResumeTextExtractor.Extracted extracted = resumeTextExtractor.extract(fileName, bytes);
        String finalTitle = isBlank(title) ? stripExtension(fileName) : title;
        Resume resume = doCreate(userId, finalTitle, extracted.text(), targetRole, fileName, extracted.fileType());
        if (parse) {
            tryParse(userId, resume, false);
        }
        return resume;
    }

    private Resume doCreate(Long userId, String title, String content, String targetRole, String fileName,
            String fileType) {
        boolean first = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)) == 0;

        LocalDateTime now = LocalDateTime.now();
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setTitle(normalizeTitle(title, first));
        resume.setContent(content);
        resume.setTargetRole(trimToNull(targetRole));
        resume.setFileName(trimToNull(fileName));
        resume.setFileType(fileType);
        resume.setIsDefault(first ? 1 : 0);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);

        if (first) {
            syncProfile(userId, resume);
        }
        indexResumeMemory(userId, resume);
        return resume;
    }

    @Transactional(rollbackFor = Exception.class)
    public Resume update(Long userId, Long resumeId, String title, String content, String targetRole) {
        Resume resume = get(userId, resumeId);
        if (!isBlank(title)) {
            resume.setTitle(normalizeTitle(title, false));
        }
        if (!isBlank(content)) {
            resume.setContent(requireContent(content));
            // 正文变了，旧的结构化解析结果就不再可信
            resume.setParsedJson(null);
        }
        if (targetRole != null) {
            resume.setTargetRole(trimToNull(targetRole));
        }
        resume.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(resume);

        if (isDefaultResume(resume)) {
            syncProfile(userId, resume);
        }
        indexResumeMemory(userId, resume);
        return resume;
    }

    @Transactional(rollbackFor = Exception.class)
    public Resume setDefault(Long userId, Long resumeId) {
        Resume target = get(userId, resumeId);
        List<Resume> all = list(userId);
        for (Resume item : all) {
            int desired = item.getId().equals(target.getId()) ? 1 : 0;
            int current = item.getIsDefault() == null ? 0 : item.getIsDefault();
            if (current != desired) {
                Resume patch = new Resume();
                patch.setId(item.getId());
                patch.setIsDefault(desired);
                resumeMapper.updateById(patch);
            }
        }
        target.setIsDefault(1);
        syncProfile(userId, target);
        return target;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long resumeId) {
        Resume resume = get(userId, resumeId);
        boolean wasDefault = isDefaultResume(resume);
        resumeMapper.deleteById(resumeId);

        if (!wasDefault) {
            return;
        }
        // 删掉默认简历后，把最近更新的一份提升为默认，避免创建面试时"没有默认简历"
        List<Resume> rest = list(userId);
        if (!rest.isEmpty()) {
            Resume next = rest.get(0);
            Resume patch = new Resume();
            patch.setId(next.getId());
            patch.setIsDefault(1);
            resumeMapper.updateById(patch);
            next.setIsDefault(1);
            syncProfile(userId, next);
        }
    }

    // ==================== 解析 ====================

    /** 显式解析：失败直接抛错，让用户知道要重试或换文件。 */
    public Resume parse(Long userId, Long resumeId) {
        Resume resume = get(userId, resumeId);
        tryParse(userId, resume, true);
        return get(userId, resumeId);
    }

    private void tryParse(Long userId, Resume resume, boolean throwOnFailure) {
        if (isBlank(resume.getContent())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历内容为空，无法解析");
        }
        try {
            String prompt = promptBuilder.buildProfilePrompt(PromptContext.builder()
                    .userId(userId)
                    .targetRole(resume.getTargetRole())
                    .resumeText(resume.getContent())
                    .task(PromptTemplates.TASK_PROFILE)
                    .build());
            String raw = resumeProfileAgent.parseProfile(prompt);
            String parsed = normalizeJson(raw);
            if (parsed == null) {
                throw new IllegalStateException("模型没有返回合法 JSON");
            }
            if (!isMeaningfulProfile(parsed)) {
                // agent 层在模型不可用时会返回一份"空壳"画像，不能当成解析成功
                throw new IllegalStateException("模型没有返回可用的简历画像（可能是模型不可用或 key 未配置）");
            }
            Resume patch = new Resume();
            patch.setId(resume.getId());
            patch.setParsedJson(parsed);
            patch.setUpdatedAt(LocalDateTime.now());
            resumeMapper.updateById(patch);
            resume.setParsedJson(parsed);

            // 简历正文没变，画像里的 resume_text 不必重写：
            // 同步只在新建、设为默认、正文更新这三处做，避免重复写 user_profile。
            indexResumeMemory(userId, resume);
        } catch (Exception ex) {
            log.warn("简历解析失败 resumeId={} userId={}", resume.getId(), userId, ex);
            if (throwOnFailure) {
                throw new BizException(ErrorCode.INTERNAL_ERROR,
                        "简历解析失败：" + ex.getMessage() + "。简历已保存，可以稍后重试解析。");
            }
        }
    }

    /**
     * 判断解析结果里是否有真实内容。
     *
     * <p>
     * 模型调用失败时 agent 层会返回一份字段全空的画像，只看"是不是合法 JSON"
     * 会把失败当成功，用户会看到"已解析"却什么都没有。
     */
    private boolean isMeaningfulProfile(String parsedJson) {
        try {
            var root = objectMapper.readTree(parsedJson);
            if (!isBlank(root.path("targetRole").asText(""))) {
                return true;
            }
            for (String arrayField : new String[] { "techStack", "projects", "strengths", "weaknesses" }) {
                var node = root.path(arrayField);
                if (node.isArray() && !node.isEmpty()) {
                    return true;
                }
            }
            var abilityMap = root.path("abilityMap");
            return abilityMap.isObject() && !abilityMap.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    // ==================== 画像与记忆同步 ====================

    private void syncProfile(Long userId, Resume resume) {
        try {
            profileService.syncResumeText(userId, resume.getContent());
        } catch (Exception ex) {
            log.warn("同步画像简历文本失败 userId={} resumeId={}", userId, resume.getId(), ex);
        }
    }

    private void indexResumeMemory(Long userId, Resume resume) {
        try {
            StringBuilder chunk = new StringBuilder()
                    .append("简历：").append(resume.getTitle()).append('\n');
            if (!isBlank(resume.getTargetRole())) {
                chunk.append("目标岗位：").append(resume.getTargetRole()).append('\n');
            }
            String content = resume.getContent() == null ? "" : resume.getContent();
            if (content.length() > MEMORY_CHUNK_MAX) {
                content = content.substring(0, MEMORY_CHUNK_MAX);
            }
            chunk.append(content);
            if (!isBlank(resume.getParsedJson())) {
                String parsed = resume.getParsedJson();
                if (parsed.length() > PARSED_PREVIEW_MAX) {
                    parsed = parsed.substring(0, PARSED_PREVIEW_MAX);
                }
                chunk.append("\n\n结构化画像：").append(parsed);
            }
            // 覆盖式写入：同一份简历只保留最新一条记忆片段，避免每次解析都堆积重复
            memoryService.replaceChunk(userId, "resume", resume.getId(), chunk.toString());
        } catch (Exception ex) {
            log.warn("写入简历记忆失败 userId={} resumeId={}", userId, resume.getId(), ex);
        }
    }

    // ==================== 工具 ====================

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
    }

    private String requireContent(String content) {
        if (isBlank(content)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历内容不能为空");
        }
        String text = content.trim();
        if (text.length() < 20) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历内容太短，至少写 20 个字");
        }
        return text;
    }

    private String normalizeTitle(String title, boolean first) {
        if (isBlank(title)) {
            return first ? "我的简历" : "未命名简历";
        }
        String text = title.trim();
        return text.length() > TITLE_MAX ? text.substring(0, TITLE_MAX) : text;
    }

    private boolean isDefaultResume(Resume resume) {
        return resume.getIsDefault() != null && resume.getIsDefault() == 1;
    }

    /** 把模型输出收敛成合法 JSON 字符串；不合法返回 null。 */
    private String normalizeJson(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String text = raw.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String json = text.substring(start, end + 1);
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception ex) {
            return null;
        }
    }

    private String stripExtension(String fileName) {
        if (isBlank(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
