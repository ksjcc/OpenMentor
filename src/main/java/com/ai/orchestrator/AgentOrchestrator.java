package com.ai.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Component;

import com.ai.agent.FollowUpAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.TechInterviewerAgent;
import com.ai.decision.DecisionAggregator;
import com.ai.decision.DifficultyPolicy;
import com.ai.decision.GraphDecisionService;
import com.ai.decision.MemoryDecisionService;
import com.ai.orchestrator.pipeline.GraphRagEnrichmentStage;
import com.ai.orchestrator.pipeline.InterviewPipelineContext;
import com.ai.orchestrator.pipeline.PromptAssemblyStage;
import com.ai.orchestrator.pipeline.RetrievalPlanningStage;
import com.ai.orchestrator.pipeline.ScoringStage;
import com.ai.orchestrator.pipeline.TraceContext;
import com.ai.orchestrator.runtime.PipelineRuntime;
import com.ai.orchestrator.runtime.PipelineStageExecutor;
import com.ai.orchestrator.runtime.PipelineStageName;
import com.ai.orchestrator.runtime.StageRecoveryResult;
import com.ai.prompt.PromptActionType;
import com.ai.prompt.PromptBudgetAllocator;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptContext;
import com.ai.prompt.PromptTemplates;
import com.ai.rag.HybridRetriever;
import com.dto.InterviewReplyDTO;
import com.dto.decision.AgentDecisionDTO;
import com.dto.decision.FinalDecisionDTO;
import com.dto.score.ScoreResultDTO;
import com.entity.Trace.TraceEvent;
import com.entity.UserProfile;
import com.service.MemoryService;
import com.service.ProfileService;
import com.service.admin.TracePersistenceService;
import com.service.garphrag.RetrievalBudgetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 多 Agent 协作决策中控层。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrator {
    private final TechInterviewerAgent techInterviewerAgent;
    private final FollowUpAgent followUpAgent;
    private final KnowledgeExtractorAgent knowledgeExtractorAgent;
    private final PromptBuilder promptBuilder;
    private final MemoryService memoryService;
    private final HybridRetriever hybridRetriever;
    private final ProfileService profileService;
    private final DifficultyPolicy difficultyPolicy;
    private final GraphDecisionService graphDecisionService;
    private final MemoryDecisionService memoryDecisionService;
    private final DecisionAggregator decisionAggregator;
    private final ScoringStage scoringStage;
    private final GraphRagEnrichmentStage graphRagEnrichmentStage;
    private final PromptAssemblyStage promptAssemblyStage;
    private final Executor llmTaskExecutor;
    private final RetrievalPlanningStage retrievalPlanningStage;
    private final RetrievalBudgetService retrievalBudgetService;
    private final TracePersistenceService tracePersistenceService;
    private final PipelineStageExecutor pipelineStageExecutor;
    private final PromptBudgetAllocator promptBudgetAllocator;

    /**
     * 生成面试第一题。
     */
    public String generateFirstQuestion(Long userId, String topic, String targetRole, String projectSummary) {
        String profileJson = memoryService.getLastProfileJson(userId);
        String summaryMd = memoryService.getLastSummaryMd(userId, "interview-extraction");
        List<String> relatedMemories = hybridRetriever.retrieve(userId, topic, 5);
        UserProfile profile = profileService.getProfile(userId);
        String effectiveResumeText = firstNonBlank(
                projectSummary,
                profile == null ? null : profile.getResumeText());

        PromptContext firstQuestionContext = PromptContext.builder()
                .userId(userId)
                .profileJson(profileJson)
                .summaryMd(summaryMd)
                .ragMemories(relatedMemories)
                .topic(topic)
                .task(PromptTemplates.TASK_NEW_QUESTION)
                .projectSummary(projectSummary)
                .targetRole(targetRole)
                .resumeText(effectiveResumeText)
                .build();
        PromptContext compressedContext = promptBudgetAllocator.compress(
                firstQuestionContext,
                PromptActionType.NEW_QUESTION);
        String input = promptBuilder.buildQuestionPrompt(compressedContext);

        return techInterviewerAgent.askQuestion(input);
    }

    /**
     * 面试结束后进行知识提炼。
     */
    public String extractKnowledge(String dialogue) {
        String input = promptBuilder.buildKnowledgeExtractionPrompt(PromptContext.builder()
                .dialogue(dialogue)
                .task(PromptTemplates.TASK_EXTRACTION)
                .build());
        return knowledgeExtractorAgent.extract(input);
    }

    /**
     * 兼容旧回答接口，内部统一走新的实时流水线。
     */
    public InterviewReplyDTO handleAnswer(Long userId, String topic, String question, String answer) {
        UserProfile profile = profileService.getProfile(userId);
        OrchestratorResult result = handleAnswerWithMultiAgentDecision(
                userId,
                null,
                topic,
                profile == null ? null : profile.getTargetRole(),
                profile == null ? null : profile.getResumeText(),
                profile == null ? null : profile.getResumeText(),
                question,
                answer,
                memoryService.getLatestProfileJson(userId),
                memoryService.getLatestSummaryMd(userId, "overall"));

        InterviewReplyDTO reply = new InterviewReplyDTO();
        reply.setAction(result.getAction());
        reply.setContent(result.getContent());
        reply.setScoreJson(result.getScoreJson());
        reply.setStageLatencyMs(result.getStageLatencyMs());
        reply.setTotalLatencyMs(result.getTotalLatencyMs());
        return reply;
    }

    /**
     * 多 Agent 协作处理用户回答。
     */
    public OrchestratorResult handleAnswerWithMultiAgentDecision(
            Long userId,
            Long sessionId,
            String topic,
            String targetRole,
            String resumeText,
            String projectSummary,
            String question,
            String answer,
            String profileJson,
            String summaryMd) {
        TraceContext trace = new TraceContext();
        trace.setTraceId(UUID.randomUUID().toString());
        OrchestratorMetrics metrics = new OrchestratorMetrics();
        PipelineRuntime runtime = new PipelineRuntime();
        runtime.initialize();
        InterviewPipelineContext context = InterviewPipelineContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .topic(topic)
                .targetRole(targetRole)
                .resumeText(resumeText)
                .projectSummary(projectSummary)
                .question(question)
                .answer(answer)
                .realTime(true)
                .retrievalBudget(retrievalBudgetService.createBudget())
                .pipelineRuntime(runtime)
                .traceContext(trace)
                .build();

        long stageStart = System.nanoTime();
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.SCORING,
                () -> scoringStage.execute(context),
                (ctx, ex) -> {
                    ctx.setScoreJson("{}");
                    ctx.setScoreResult(new ScoreResultDTO());
                    return StageRecoveryResult.defaultValue(ex.getMessage(), "use empty score result");
                });
        metrics.recordStage("scoring", stageStart);

        context.setWeakTopic(resolveWeakTopic(context.getScoreResult(), topic));

        stageStart = System.nanoTime();
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.RETRIEVAL_PLANNING,
                () -> retrievalPlanningStage.execute(context),
                (ctx, ex) -> {
                    ctx.setRetrievalPlan(com.dto.Retrieval.RetrievalPlanDTO.builder()
                            .primaryRetriever("MEMORY_RAG")
                            .needMemory(true)
                            .needVector(false)
                            .needGraph(false)
                            .needHyde(false)
                            .enableKeywordFallback(true)
                            .topK(2)
                            .query(firstNonBlank(ctx.getWeakTopic(), ctx.getQuestion(), ""))
                            .reason("runtime fallback retrieval plan")
                            .retrievalBudget(1)
                            .build());
                    return StageRecoveryResult.fallback(ex.getMessage(), "fallback to conservative retrieval plan");
                });
        metrics.recordStage("retrievalPlanning", stageStart);

        stageStart = System.nanoTime();
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.GRAPH_RAG_ENRICHMENT,
                () -> graphRagEnrichmentStage.execute(context),
                (ctx, ex) -> {
                    ctx.setRetrievalContext(List.of());
                    ctx.setGraphRagContext(List.of());
                    ctx.setGraphFallbackTriggered(true);
                    return StageRecoveryResult.fallback(ex.getMessage(), "skip graph enrichment and continue");
                });
        metrics.recordStage("graphRagEnrichment", stageStart);

        stageStart = System.nanoTime();
        final List<AgentDecisionDTO>[] decisionHolder = new List[1];
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.DECISIONING,
                () -> decisionHolder[0] = buildDecisions(userId, context.getWeakTopic(), context.getScoreResult()),
                (ctx, ex) -> {
                    decisionHolder[0] = buildDecisionsSerial(userId, context.getWeakTopic(), context.getScoreResult());
                    return StageRecoveryResult.fallback(ex.getMessage(), "fallback to serial decisioning");
                });
        List<AgentDecisionDTO> decisions = decisionHolder[0] == null ? List.of() : decisionHolder[0];
        metrics.recordStage("decisioning", stageStart);

        stageStart = System.nanoTime();
        final FinalDecisionDTO[] finalDecisionHolder = new FinalDecisionDTO[1];
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.AGGREGATION,
                () -> finalDecisionHolder[0] = decisionAggregator.aggregate(decisions),
                (ctx, ex) -> {
                    FinalDecisionDTO fallbackDecision = new FinalDecisionDTO();
                    fallbackDecision.setAction(com.ai.decision.DecisionAction.NEXT);
                    fallbackDecision.setReason("aggregation fallback to NEXT");
                    fallbackDecision.setTopic(firstNonBlank(ctx.getWeakTopic(), ctx.getTopic(), ""));
                    finalDecisionHolder[0] = fallbackDecision;
                    return StageRecoveryResult.defaultValue(ex.getMessage(), "use NEXT as fallback action");
                });
        FinalDecisionDTO finalDecision = finalDecisionHolder[0];
        metrics.recordStage("aggregation", stageStart);

        stageStart = System.nanoTime();
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.PROMPT_ASSEMBLY,
                () -> promptAssemblyStage.execute(context, profileJson, summaryMd, finalDecision),
                (ctx, ex) -> {
                    ctx.setPromptContext(PromptContext.builder()
                            .userId(ctx.getUserId())
                            .sessionId(ctx.getSessionId())
                            .profileJson(profileJson)
                            .summaryMd(summaryMd)
                            .ragMemories(ctx.getRetrievalContext() == null ? List.of() : ctx.getRetrievalContext())
                            .recentContext("暂无短期上下文。")
                            .currentQuestion(ctx.getQuestion())
                            .currentAnswer(ctx.getAnswer())
                            .scoreJson(ctx.getScoreJson())
                            .topic(finalDecision == null ? ctx.getTopic() : finalDecision.getTopic())
                            .targetRole(ctx.getTargetRole())
                            .resumeText(ctx.getResumeText())
                            .projectSummary(ctx.getProjectSummary())
                            .graphReasoning(finalDecision == null ? "" : finalDecision.getReason())
                            .build());
                    return StageRecoveryResult.defaultValue(ex.getMessage(), "use simplified prompt context");
                });
        metrics.recordStage("promptAssembly", stageStart);

        stageStart = System.nanoTime();
        final String[] nextContentHolder = new String[1];
        pipelineStageExecutor.execute(
                context,
                PipelineStageName.GENERATION,
                () -> nextContentHolder[0] = generateNextContent(context, finalDecision),
                (ctx, ex) -> {
                    nextContentHolder[0] = "我先基于当前上下文继续这轮面试。请你简要补充刚才这道题的核心思路。";
                    return StageRecoveryResult.defaultValue(ex.getMessage(), "use safe fallback generation");
                });
        String nextContent = nextContentHolder[0];
        metrics.recordStage("generation", stageStart);

        OrchestratorResult result = new OrchestratorResult();
        result.setAction(finalDecision.getAction().name());
        result.setContent(nextContent);
        result.setScoreJson(context.getScoreJson());
        result.setDecisionReason(finalDecision.getReason());
        result.setAgentDecisions(decisions);
        result.setStageLatencyMs(metrics.snapshotStageLatencyMs());
        result.setTotalLatencyMs(metrics.totalLatencyMs());
        tracePersistenceService.persist(
                context.getTraceContext(),
                userId,
                sessionId);
        log.info("orchestrator latency total={}ms stages={}", result.getTotalLatencyMs(), result.getStageLatencyMs());
        return result;
    }

    private List<AgentDecisionDTO> buildDecisions(
            Long userId,
            String weakTopic,
            ScoreResultDTO scoreResult) {
        AgentDecisionDTO difficultyDecision = difficultyPolicy.decide(scoreResult);

        CompletableFuture<AgentDecisionDTO> memoryDecisionFuture = CompletableFuture.supplyAsync(
                () -> memoryDecisionService.decide(userId, weakTopic),
                llmTaskExecutor);
        CompletableFuture<AgentDecisionDTO> graphDecisionFuture = CompletableFuture.supplyAsync(
                () -> graphDecisionService.decide(userId, weakTopic),
                llmTaskExecutor);

        AgentDecisionDTO memoryDecision = memoryDecisionFuture.join();
        AgentDecisionDTO graphDecision = graphDecisionFuture.join();

        List<AgentDecisionDTO> decisions = new ArrayList<>();
        decisions.add(difficultyDecision);
        decisions.add(memoryDecision);
        decisions.add(graphDecision);
        return decisions;
    }

    public Map<String, Long> benchmarkDecisionFlow(
            Long userId,
            String weakTopic,
            ScoreResultDTO scoreResult) {
        Map<String, Long> benchmark = new LinkedHashMap<>();

        long serialStart = System.nanoTime();
        buildDecisionsSerial(userId, weakTopic, scoreResult);
        benchmark.put("serialDecisionMs", elapsedMillis(serialStart));

        long parallelStart = System.nanoTime();
        buildDecisions(userId, weakTopic, scoreResult);
        benchmark.put("parallelDecisionMs", elapsedMillis(parallelStart));

        return benchmark;
    }

    private List<AgentDecisionDTO> buildDecisionsSerial(
            Long userId,
            String weakTopic,
            ScoreResultDTO scoreResult) {
        List<AgentDecisionDTO> decisions = new ArrayList<>();
        decisions.add(difficultyPolicy.decide(scoreResult));
        decisions.add(memoryDecisionService.decide(userId, weakTopic));
        decisions.add(graphDecisionService.decide(userId, weakTopic));
        return decisions;
    }

    private String resolveWeakTopic(ScoreResultDTO scoreResult, String fallbackTopic) {
        if (scoreResult != null
                && scoreResult.getWeakPoints() != null
                && !scoreResult.getWeakPoints().isEmpty()
                && !isBlank(scoreResult.getWeakPoints().get(0))) {
            return scoreResult.getWeakPoints().get(0);
        }
        return fallbackTopic;
    }

    private String generateNextContent(InterviewPipelineContext context, FinalDecisionDTO finalDecision) {
        long start = System.currentTimeMillis();
        PromptContext promptContext = context.getPromptContext();
        String stageInput;
        String agentName;
        String output;

        switch (finalDecision.getAction()) {
            case FOLLOW_UP -> {
                stageInput = promptBuilder.buildFollowUpPrompt(promptContext);
                agentName = "followUpAgent";
                output = followUpAgent.askFollowUp(stageInput);
            }
            case HARDER -> {
                stageInput = promptBuilder.buildHarderQuestionPrompt(promptContext);
                agentName = "techInterviewerAgent";
                output = techInterviewerAgent.askQuestion(stageInput);
            }
            case NEXT -> {
                stageInput = promptBuilder.buildQuestionPrompt(promptContext);
                agentName = "techInterviewerAgent";
                output = techInterviewerAgent.askQuestion(stageInput);
            }
            case FINISH -> {
                stageInput = finalDecision.getReason() == null ? "" : finalDecision.getReason();
                agentName = "system";
                output = "本轮面试已完成，接下来将为你生成面试报告和复习建议。";
            }
            default -> throw new IllegalStateException("Unexpected value: " + finalDecision.getAction());
        }

        if (context.getTraceContext() != null) {
            long end = System.currentTimeMillis();
            TraceEvent event = TraceEvent.builder()
                    .stage("generation")
                    .startTime(start)
                    .endTime(end)
                    .latencyMs(end - start)
                    .input(stageInput)
                    .output(output)
                    .metadata(Map.of(
                            "action", finalDecision.getAction().name(),
                            "agent", agentName,
                            "topic",
                            promptContext == null || promptContext.getTopic() == null ? "" : promptContext.getTopic(),
                            "graphMode", context.getUsedGraphMode() == null ? "" : context.getUsedGraphMode().name(),
                            "fallbackTriggered", context.isGraphFallbackTriggered(),
                            "promptLength", stageInput == null ? 0 : stageInput.length(),
                            "outputLength", output == null ? 0 : output.length(),
                            "decisionReason", finalDecision.getReason() == null ? "" : finalDecision.getReason()))
                    .build();
            context.getTraceContext().addEvent(event);
        }

        return output;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
