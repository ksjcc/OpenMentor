package com.ai.rag;

import org.springframework.stereotype.Component;

import com.ai.memory.MemoryLayer;
import com.ai.memory.MemoryLayerRouteDecision;
import com.ai.memory.MemoryLayerRouterService;
import com.ai.memory.MemoryStorageType;
import com.common.GraphRagMode;
import com.dto.Retrieval.RetrievalPlanDTO;
import com.entity.InterviewSession;
import com.entity.UserProfile;
import com.mapper.InterviewSessionMapper;
import com.service.MemoryService;
import com.service.ProfileService;
import com.service.cache.InterviewMemoryCacheService;
import com.service.garphrag.GraphRagRetriever;
import com.service.garphrag.RetrievalBudget;
import com.service.garphrag.RetrievalBudgetService;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 混合检索器
 *
 * 作用：
 * 1. 按检索计划决定主检索和辅助检索
 * 2. MemoryRAG 负责用户画像、历史 interview、长期记忆
 * 3. VectorRAG 负责通用技术知识召回，可选 HyDE
 * 4. GraphRAG 负责关系、多跳和推理型上下文
 * 5. 必要时再补轻量 keyword fallback，并统一重排
 */
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorStoreService vectorStoreService;
    private final MemoryService memoryService;
    private final ProfileService profileService;
    private final InterviewMemoryCacheService interviewMemoryCacheService;
    private final InterviewSessionMapper interviewSessionMapper;
    private final GraphRagRetriever graphRagRetriever;
    private final RetrievalBudgetService retrievalBudgetService;
    private final HydeQueryExpansionService hydeQueryExpansionService;
    private final RerankService rerankService;
    private final MemoryLayerRouterService memoryLayerRouterService;

    /**
     * 检索用户相关记忆
     *
     * @param userId 用户ID
     * @param query  当前主题 / 用户问题
     * @param limit  最大返回数量
     * @return 记忆文本列表
     */
    public List<String> retrieve(Long userId, String query, int topK) {
        if (userId == null || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        RetrievalPlanDTO defaultPlan = RetrievalPlanDTO.builder()
                .primaryRetriever("VECTOR_RAG")
                .needMemory(true)
                .needVector(true)
                .needGraph(false)
                .needHyde(true)
                .enableKeywordFallback(true)
                .graphMode(GraphRagMode.LOCAL)
                .topK(topK)
                .query(query)
                .reason("default hybrid retrieval plan")
                .build();

        return retrieve(userId, defaultPlan);
    }

    public List<String> retrieve(Long userId, RetrievalPlanDTO plan) {
        return retrieve(userId, plan, null);
    }

    public List<String> retrieve(Long userId, RetrievalPlanDTO plan, RetrievalBudget budget) {
        return retrieve(userId, null, plan, budget);
    }

    public List<String> retrieve(Long userId, Long sessionId, RetrievalPlanDTO plan, RetrievalBudget budget) {
        if (userId == null || plan == null || isBlank(plan.getQuery())) {
            return List.of();
        }

        String query = plan.getQuery();
        int topK = safeTopK(plan.getTopK());
        RetrievalBudget effectiveBudget = budget == null ? retrievalBudgetService.createBudget() : budget;
        if (effectiveBudget.isTimedOut()) {
            return List.of();
        }
        String vectorQuery = plan.isNeedHyde()
                ? hydeQueryExpansionService.expand(query)
                : query;
        Set<String> merged = new LinkedHashSet<>();

        runPrimaryRetriever(plan, userId, sessionId, query, vectorQuery, topK, effectiveBudget, merged);
        runSecondaryRetrievers(plan, userId, sessionId, query, vectorQuery, topK, effectiveBudget, merged);

        if (plan.isEnableKeywordFallback() && merged.size() < topK) {
            addAllNonBlank(merged, keywordFallback(userId, query, topK));
        }

        if (merged.isEmpty()) {
            return List.of();
        }

        List<String> reranked = rerankService.rerank(query, new ArrayList<>(merged), topK, effectiveBudget);
        return applyGuard(reranked, topK, effectiveBudget);
    }

    private void runPrimaryRetriever(
            RetrievalPlanDTO plan,
            Long userId,
            Long sessionId,
            String query,
            String vectorQuery,
            int topK,
            RetrievalBudget budget,
            Set<String> merged) {
        String primary = normalizePrimary(plan.getPrimaryRetriever());
        switch (primary) {
            case "MEMORY_RAG" -> addAllNonBlank(merged, retrieveMemory(userId, sessionId, query, plan, topK, budget));
            case "GRAPH_RAG" -> addAllNonBlank(merged, retrieveGraph(userId, query, plan, topK, budget));
            case "VECTOR_RAG" -> addAllNonBlank(merged, retrieveVector(userId, vectorQuery, topK, budget));
            default -> addAllNonBlank(merged, retrieveVector(userId, vectorQuery, topK, budget));
        }
    }

    private void runSecondaryRetrievers(
            RetrievalPlanDTO plan,
            Long userId,
            Long sessionId,
            String query,
            String vectorQuery,
            int topK,
            RetrievalBudget budget,
            Set<String> merged) {
        String primary = normalizePrimary(plan.getPrimaryRetriever());

        if (plan.isNeedMemory() && !"MEMORY_RAG".equals(primary)) {
            addAllNonBlank(merged, retrieveMemory(userId, sessionId, query, plan, topK, budget));
        }

        if (plan.isNeedVector() && !"VECTOR_RAG".equals(primary)) {
            addAllNonBlank(merged, retrieveVector(userId, vectorQuery, topK, budget));
        }

        if (plan.isNeedGraph() && !"GRAPH_RAG".equals(primary)) {
            addAllNonBlank(merged, retrieveGraph(userId, query, plan, topK, budget));
        }
    }

    private List<String> retrieveMemory(
            Long userId,
            Long sessionId,
            String query,
            RetrievalPlanDTO plan,
            int topK,
            RetrievalBudget budget) {
        if (!allowRetrieverCall(budget)) {
            return List.of();
        }
        MemoryLayerRouteDecision route = resolveRoute(query, plan);
        List<String> primary = retrieveByStorages(
                userId,
                sessionId,
                query,
                topK,
                route == null ? List.of() : route.getSelectedStorage(),
                budget);
        if (!primary.isEmpty()) {
            return primary;
        }
        return retrieveByStorages(
                userId,
                sessionId,
                query,
                topK,
                route == null ? List.of() : route.getFallbackStorage(),
                budget);
    }

    private List<String> retrieveVector(Long userId, String vectorQuery, int topK, RetrievalBudget budget) {
        if (!allowRetrieverCall(budget)) {
            return List.of();
        }
        return vectorStoreService.search(userId, vectorQuery, topK);
    }

    private List<String> retrieveGraph(
            Long userId,
            String query,
            RetrievalPlanDTO plan,
            int topK,
            RetrievalBudget budget) {
        GraphRagMode mode = plan.getGraphMode() == null ? GraphRagMode.AUTO : plan.getGraphMode();
        return graphRagRetriever.retrieve(
                userId,
                query,
                mode,
                false,
                topK,
                budget);
    }

    private List<String> applyGuard(List<String> candidates, int topK, RetrievalBudget budget) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int limit = topK <= 0 ? candidates.size() : topK;
        if (budget == null) {
            return candidates.stream().limit(limit).toList();
        }

        List<String> guarded = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (budget.isTimedOut()) {
                break;
            }
            if (!budget.consumeChunk(1)) {
                break;
            }
            guarded.add(candidate);
            if (guarded.size() >= limit) {
                break;
            }
        }
        return guarded;
    }

    private List<String> keywordFallback(Long userId, String query, int topK) {
        Set<String> fallback = new LinkedHashSet<>();
        addAllNonBlank(fallback, memoryService.retrieveRelevantMemories(userId, query));

        for (String token : splitKeywords(query)) {
            if (fallback.size() >= topK) {
                break;
            }
            addAllNonBlank(fallback, memoryService.retrieveRelevantMemories(userId, token));
        }

        return fallback.stream()
                .limit(topK)
                .toList();
    }

    private List<String> splitKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return Arrays.stream(query
                .toLowerCase(Locale.ROOT)
                .split("[\\s,，。；;、:/\\\\()（）\\-]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .limit(6)
                .toList();
    }

    private void addAllNonBlank(Set<String> target, List<String> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String item : source) {
            if (item != null && !item.isBlank()) {
                target.add(item.trim());
            }
        }
    }

    private int safeTopK(Integer topK) {
        return topK == null || topK <= 0 ? 5 : topK;
    }

    private MemoryLayerRouteDecision resolveRoute(String query, RetrievalPlanDTO plan) {
        if (plan != null
                && plan.getMemoryLayer() != null
                && plan.getSelectedStorage() != null
                && !plan.getSelectedStorage().isEmpty()) {
            return MemoryLayerRouteDecision.builder()
                    .primaryLayer(plan.getMemoryLayer())
                    .fallbackLayer(plan.getFallbackMemoryLayer())
                    .selectedStorage(plan.getSelectedStorage())
                    .fallbackStorage(plan.getFallbackStorage())
                    .reason(plan.getReason())
                    .build();
        }
        return memoryLayerRouterService.route(query, plan);
    }

    private List<String> retrieveByStorages(
            Long userId,
            Long sessionId,
            String query,
            int topK,
            List<String> storages,
            RetrievalBudget budget) {
        if (storages == null || storages.isEmpty()) {
            return List.of();
        }

        Set<String> merged = new LinkedHashSet<>();
        for (String storageName : storages) {
            if (storageName == null || storageName.isBlank()) {
                continue;
            }
            MemoryStorageType storageType;
            try {
                storageType = MemoryStorageType.valueOf(storageName);
            } catch (Exception ignore) {
                continue;
            }
            addAllNonBlank(merged, retrieveFromStorage(userId, sessionId, query, topK, storageType, budget));
            if (merged.size() >= topK) {
                break;
            }
        }
        return merged.stream().limit(topK).toList();
    }

    private List<String> retrieveFromStorage(
            Long userId,
            Long sessionId,
            String query,
            int topK,
            MemoryStorageType storageType,
            RetrievalBudget budget) {
        return switch (storageType) {
            case REDIS_SESSION_CACHE -> retrieveSessionContext(sessionId);
            case MEMORY_SUMMARY_MD -> retrieveGrowthSummaries(userId);
            case MEMORY_CHUNK -> memoryService.retrieveRelevantMemories(userId, query);
            case PGVECTOR -> retrieveVector(userId, query, topK, budget);
            case INTERVIEW_HISTORY -> retrieveInterviewHistory(userId);
            case MEMORY_PROFILE_JSON -> retrieveProfileJson(userId);
            case USER_PROFILE -> retrieveUserProfile(userId);
        };
    }

    private List<String> retrieveSessionContext(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        String context = interviewMemoryCacheService.buildRecentContext(sessionId);
        return isBlank(context) || "暂无短期上下文。".equals(context)
                ? List.of()
                : List.of(context);
    }

    private List<String> retrieveGrowthSummaries(Long userId) {
        List<String> results = new ArrayList<>();
        String overall = memoryService.getLatestSummaryMd(userId, "overall");
        String extraction = memoryService.getLatestSummaryMd(userId, "interview-extraction");
        addIfPresent(results, overall);
        addIfPresent(results, extraction);
        return results;
    }

    private List<String> retrieveInterviewHistory(Long userId) {
        List<InterviewSession> sessions = interviewSessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getId)
                        .last("LIMIT 3"));
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (InterviewSession session : sessions) {
            if (session == null) {
                continue;
            }
            addIfPresent(results, session.getSummary());
            addIfPresent(results, session.getTranscript());
        }
        return results;
    }

    private List<String> retrieveProfileJson(Long userId) {
        String profileJson = memoryService.getLatestProfileJson(userId);
        return isBlank(profileJson) ? List.of() : List.of(profileJson);
    }

    private List<String> retrieveUserProfile(Long userId) {
        UserProfile profile = profileService.getProfile(userId);
        if (profile == null) {
            return List.of();
        }
        String content = """
                targetRole=%s
                experienceLevel=%s
                learningPreference=%s
                techStack=%s
                abilityMap=%s
                weaknesses=%s
                strengths=%s
                resumeText=%s
                """.formatted(
                safe(profile.getTargetRole()),
                safe(profile.getExperienceLevel()),
                safe(profile.getLearningPreference()),
                safe(profile.getTechStack()),
                safe(profile.getAbilityMap()),
                safe(profile.getWeaknesses()),
                safe(profile.getStrengths()),
                safe(profile.getResumeText()));
        return List.of(content.trim());
    }

    private void addIfPresent(List<String> target, String value) {
        if (target == null || isBlank(value)) {
            return;
        }
        target.add(value.trim());
    }

    private boolean allowRetrieverCall(RetrievalBudget budget) {
        return budget == null || budget.consumeRetrieverCall();
    }

    private String normalizePrimary(String primaryRetriever) {
        if (isBlank(primaryRetriever)) {
            return "VECTOR_RAG";
        }
        return primaryRetriever.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
