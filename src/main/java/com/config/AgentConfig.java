package com.config;

import com.ai.agent.CommunityReportAgent;
import com.ai.agent.DriftQuestionAgent;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.GraphExtractionAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.ResumeProfileAgent;
import com.ai.agent.ScoreAgent;
import com.ai.agent.TechInterviewerAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public TechInterviewerAgent techInterviewerAgent(ChatLanguageModel model) {
        return AiServices.create(TechInterviewerAgent.class, model);
    }

    @Bean
    public ScoreAgent scoreAgent(ChatLanguageModel model) {
        return AiServices.create(ScoreAgent.class, model);
    }

    @Bean
    public FollowUpAgent followUpAgent(ChatLanguageModel model) {
        return AiServices.create(FollowUpAgent.class, model);
    }

    @Bean
    public KnowledgeExtractorAgent knowledgeExtractorAgent(ChatLanguageModel model) {
        return AiServices.create(KnowledgeExtractorAgent.class, model);
    }

    @Bean
    public ResumeProfileAgent resumeProfileAgent(ChatLanguageModel model) {
        return AiServices.create(ResumeProfileAgent.class, model);
    }

    @Bean
    public GraphExtractionAgent graphExtractionAgent(ChatLanguageModel model) {
        return AiServices.create(GraphExtractionAgent.class, model);
    }

    @Bean
    public CommunityReportAgent CommunityReportAgent(ChatLanguageModel model) {
        return AiServices.create(CommunityReportAgent.class, model);
    }

    @Bean
    public DriftQuestionAgent DriftQuestionAgent(ChatLanguageModel model) {
        return AiServices.create(DriftQuestionAgent.class, model);
    }

    @Bean
    public Executor llmTaskExecutor() {
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }
}
