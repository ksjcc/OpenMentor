package com.ai.orchestrator;

import com.ai.agent.CareerMentorAgent;
import com.ai.agent.FollowUpAgent;
import com.ai.agent.KnowledgeExtractorAgent;
import com.ai.agent.MemoryUpdateAgent;
import com.ai.agent.ReviewPlannerAgent;
import com.ai.agent.ScoreAgent;
import com.ai.agent.TechInterviewerAgent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentOrchestrator {

    private final List<String> agentNames;

    public AgentOrchestrator(
            TechInterviewerAgent techInterviewerAgent,
            ScoreAgent scoreAgent,
            FollowUpAgent followUpAgent,
            KnowledgeExtractorAgent knowledgeExtractorAgent,
            MemoryUpdateAgent memoryUpdateAgent,
            ReviewPlannerAgent reviewPlannerAgent,
            CareerMentorAgent careerMentorAgent) {
        this.agentNames = List.of(
            techInterviewerAgent.name(),
            scoreAgent.name(),
            followUpAgent.name(),
            knowledgeExtractorAgent.name(),
            memoryUpdateAgent.name(),
            reviewPlannerAgent.name(),
            careerMentorAgent.name()
        );
    }

    public List<String> availableAgents() {
        return agentNames;
    }
}
