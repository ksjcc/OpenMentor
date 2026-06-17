package com.config;

import com.ai.agent.CommunityReportAgent;
import com.ai.agent.DriftQuestionAgent;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.GraphExtractionAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.ResumeProfileAgent;
import com.ai.agent.ReviewPlannerAgent;
import com.ai.agent.ScoreAgent;
import com.ai.agent.TechInterviewerAgent;
import com.ai.agent.support.AgentFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public TechInterviewerAgent techInterviewerAgent(AgentFactory agentFactory) {
        return agentFactory.createTechInterviewerAgent();
    }

    @Bean
    public ScoreAgent scoreAgent(AgentFactory agentFactory) {
        return agentFactory.createScoreAgent();
    }

    @Bean
    public FollowUpAgent followUpAgent(AgentFactory agentFactory) {
        return agentFactory.createFollowUpAgent();
    }

    @Bean
    public KnowledgeExtractorAgent knowledgeExtractorAgent(AgentFactory agentFactory) {
        return agentFactory.createKnowledgeExtractorAgent();
    }

    @Bean
    public ResumeProfileAgent resumeProfileAgent(AgentFactory agentFactory) {
        return agentFactory.createResumeProfileAgent();
    }

    @Bean
    public GraphExtractionAgent graphExtractionAgent(AgentFactory agentFactory) {
        return agentFactory.createGraphExtractionAgent();
    }

    @Bean
    public CommunityReportAgent communityReportAgent(AgentFactory agentFactory) {
        return agentFactory.createCommunityReportAgent();
    }

    @Bean
    public DriftQuestionAgent driftQuestionAgent(AgentFactory agentFactory) {
        return agentFactory.createDriftQuestionAgent();
    }

    @Bean
    public ReviewPlannerAgent reviewPlannerAgent(AgentFactory agentFactory) {
        return agentFactory.createReviewPlannerAgent();
    }

    @Bean
    public Executor llmTaskExecutor() {
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }
}
