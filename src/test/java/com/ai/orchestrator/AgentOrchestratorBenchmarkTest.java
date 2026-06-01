package com.ai.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.agent.FollowUpAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.TechInterviewerAgent;
import com.ai.decision.DecisionAction;
import com.ai.decision.DecisionAggregator;
import com.ai.decision.DifficultyPolicy;
import com.ai.decision.GraphDecisionService;
import com.ai.decision.MemoryDecisionService;
import com.ai.orchestrator.pipeline.GraphRagEnrichmentStage;
import com.ai.orchestrator.pipeline.PromptAssemblyStage;
import com.ai.orchestrator.pipeline.RetrievalPlanningStage;
import com.ai.orchestrator.pipeline.ScoringStage;
import com.ai.prompt.PromptBuilder;
import com.ai.prompt.PromptBudgetAllocator;
import com.ai.orchestrator.runtime.PipelineStageExecutor;
import com.dto.decision.AgentDecisionDTO;
import com.dto.score.ScoreResultDTO;
import com.ai.rag.HybridRetriever;
import com.service.MemoryService;
import com.service.ProfileService;
import com.service.admin.TracePersistenceService;
import com.service.garphrag.RetrievalBudgetService;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorBenchmarkTest {

    private static final Executor TEST_EXECUTOR = Executors.newFixedThreadPool(4);

    @Mock
    private TechInterviewerAgent techInterviewerAgent;

    @Mock
    private FollowUpAgent followUpAgent;

    @Mock
    private KnowledgeExtractorAgent knowledgeExtractorAgent;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private MemoryService memoryService;

    @Mock
    private HybridRetriever hybridRetriever;

    @Mock
    private ProfileService profileService;

    @Mock
    private DifficultyPolicy difficultyPolicy;

    @Mock
    private GraphDecisionService graphDecisionService;

    @Mock
    private MemoryDecisionService memoryDecisionService;

    @Mock
    private ScoringStage scoringStage;

    @Mock
    private GraphRagEnrichmentStage graphRagEnrichmentStage;

    @Mock
    private PromptAssemblyStage promptAssemblyStage;

    @Mock
    private RetrievalPlanningStage retrievalPlanningStage;

    @Mock
    private RetrievalBudgetService retrievalBudgetService;

    @Mock
    private TracePersistenceService tracePersistenceService;

    @Mock
    private PipelineStageExecutor pipelineStageExecutor;

    @Mock
    private PromptBudgetAllocator promptBudgetAllocator;

    private final DecisionAggregator decisionAggregator = new DecisionAggregator();

    @BeforeEach
    void setUp() {
        lenient().when(promptBudgetAllocator.compress(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(pipelineStageExecutor).execute(any(), any(), any(), any());
    }

    @AfterAll
    static void shutdownExecutor() {
        if (TEST_EXECUTOR instanceof java.util.concurrent.ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    @Test
    void benchmarkDecisionFlowShowsParallelImprovement() throws Exception {
        AgentDecisionDTO difficultyDecision = buildDecision("DIFFICULTY_POLICY", DecisionAction.FOLLOW_UP, 0.8,
                "需要继续追问", "Redis");
        AgentDecisionDTO memoryDecision = buildDecision("MEMORY_AGENT", DecisionAction.FOLLOW_UP, 0.85,
                "长期记忆显示该知识点薄弱", "Redis");
        AgentDecisionDTO graphDecision = buildDecision("GRAPH_REASONING", DecisionAction.FOLLOW_UP, 0.9,
                "图谱推理建议继续追问", "Redis");

        when(difficultyPolicy.decide(any())).thenReturn(difficultyDecision);
        doAnswer(invocation -> {
            Thread.sleep(220L);
            return memoryDecision;
        }).when(memoryDecisionService).decide(anyLong(), anyString());
        doAnswer(invocation -> {
            Thread.sleep(220L);
            return graphDecision;
        }).when(graphDecisionService).decide(anyLong(), anyString());

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                techInterviewerAgent,
                followUpAgent,
                knowledgeExtractorAgent,
                promptBuilder,
                memoryService,
                hybridRetriever,
                profileService,
                difficultyPolicy,
                graphDecisionService,
                memoryDecisionService,
                decisionAggregator,
                scoringStage,
                graphRagEnrichmentStage,
                promptAssemblyStage,
                TEST_EXECUTOR,
                retrievalPlanningStage,
                retrievalBudgetService,
                tracePersistenceService,
                pipelineStageExecutor,
                promptBudgetAllocator);

        ScoreResultDTO scoreResult = new ScoreResultDTO();
        scoreResult.setScore(58);
        scoreResult.setWeakPoints(List.of("Redis"));

        Map<String, Long> benchmark = orchestrator.benchmarkDecisionFlow(1L, "Redis", scoreResult);
        long serialDecisionMs = benchmark.get("serialDecisionMs");
        long parallelDecisionMs = benchmark.get("parallelDecisionMs");

        assertThat(serialDecisionMs).isGreaterThanOrEqualTo(400L);
        assertThat(parallelDecisionMs).isLessThan(serialDecisionMs - 100L);
    }

    @Test
    void handleAnswerWithMultiAgentDecisionReturnsLatencyMetrics() {
        AgentDecisionDTO difficultyDecision = buildDecision("DIFFICULTY_POLICY", DecisionAction.NEXT, 0.8,
                "进入下一题", "JVM");
        AgentDecisionDTO memoryDecision = buildDecision("MEMORY_AGENT", DecisionAction.NEXT, 0.5,
                "长期记忆没有明显薄弱证据", "JVM");
        AgentDecisionDTO graphDecision = buildDecision("GRAPH_REASONING", DecisionAction.NEXT, 0.5,
                "图谱未发现更深层节点", "JVM");

        when(difficultyPolicy.decide(any())).thenReturn(difficultyDecision);
        when(memoryDecisionService.decide(anyLong(), anyString())).thenReturn(memoryDecision);
        when(graphDecisionService.decide(anyLong(), anyString())).thenReturn(graphDecision);
        when(promptBuilder.buildQuestionPrompt(any())).thenReturn("next-question-prompt");
        when(techInterviewerAgent.askQuestion("next-question-prompt")).thenReturn("请解释一下 JVM 内存模型。");

        doAnswer(invocation -> {
            com.ai.orchestrator.pipeline.InterviewPipelineContext context = invocation.getArgument(0);
            ScoreResultDTO scoreResult = new ScoreResultDTO();
            scoreResult.setScore(78);
            scoreResult.setWeakPoints(List.of("JVM"));
            context.setScoreJson("{\"score\":78}");
            context.setScoreResult(scoreResult);
            return null;
        }).when(scoringStage).execute(any());

        doAnswer(invocation -> {
            com.ai.orchestrator.pipeline.InterviewPipelineContext context = invocation.getArgument(0);
            context.setGraphRagContext(List.of("JVM 调优案例"));
            return null;
        }).when(graphRagEnrichmentStage).execute(any());

        doAnswer(invocation -> {
            com.ai.orchestrator.pipeline.InterviewPipelineContext context = invocation.getArgument(0);
            com.dto.decision.FinalDecisionDTO finalDecision = invocation.getArgument(3);
            context.setPromptContext(com.ai.prompt.PromptContext.builder()
                    .topic(finalDecision.getTopic())
                    .currentQuestion(context.getQuestion())
                    .currentAnswer(context.getAnswer())
                    .ragMemories(context.getGraphRagContext())
                    .scoreJson(context.getScoreJson())
                    .build());
            return null;
        }).when(promptAssemblyStage).execute(any(), any(), any(), any());

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                techInterviewerAgent,
                followUpAgent,
                knowledgeExtractorAgent,
                promptBuilder,
                memoryService,
                hybridRetriever,
                profileService,
                difficultyPolicy,
                graphDecisionService,
                memoryDecisionService,
                decisionAggregator,
                scoringStage,
                graphRagEnrichmentStage,
                promptAssemblyStage,
                TEST_EXECUTOR,
                retrievalPlanningStage,
                retrievalBudgetService,
                tracePersistenceService,
                pipelineStageExecutor,
                promptBudgetAllocator);

        OrchestratorResult result = orchestrator.handleAnswerWithMultiAgentDecision(
                1L,
                99L,
                "JVM",
                "Java后端开发",
                "做过订单系统、缓存和消息队列相关项目。",
                "负责订单系统的缓存与异步链路优化。",
                "什么是 JVM 内存模型？",
                "它主要分堆和栈。",
                "{}",
                "summary");

        assertThat(result.getContent()).isEqualTo("请解释一下 JVM 内存模型。");
        assertThat(result.getTotalLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.getStageLatencyMs())
                .containsKeys("scoring", "graphRagEnrichment", "decisioning", "aggregation", "promptAssembly",
                        "generation");
    }

    private AgentDecisionDTO buildDecision(
            String source,
            DecisionAction action,
            double confidence,
            String reason,
            String topic) {
        AgentDecisionDTO decision = new AgentDecisionDTO();
        decision.setSource(source);
        decision.setAction(action);
        decision.setConfidence(confidence);
        decision.setReason(reason);
        decision.setRecommendedTopic(topic);
        return decision;
    }
}
