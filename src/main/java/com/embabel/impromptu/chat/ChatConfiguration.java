package com.embabel.impromptu.chat;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.chat.Chatbot;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.dice.common.Relations;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.projection.memory.support.DefaultMemoryProjector;
import com.embabel.dice.projection.memory.support.RelationBasedKnowledgeTypeClassifier;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.user.DrivineImpromptuUserService;
import com.embabel.impromptu.user.ImpromptuUserService;
import org.drivine.manager.GraphObjectManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure a chatbot that responds uses all actions available on the AgentPlatform
 */
@Configuration
class ChatConfiguration {

    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform) {
        return AgentProcessChatbot.utilityFromPlatform(
                agentPlatform,
                new Verbosity().showPrompts()
        );
    }

    @Bean
    ImpromptuUserService impromptuUserService(GraphObjectManager gom) {
        return new DrivineImpromptuUserService(gom);
    }

    @Bean
    MemoryProjector memoryProjector(
            ImpromptuUserService userService,
            PropositionRepository propositionRepository,
            Relations relations) {
        return DefaultMemoryProjector
                .against(propositionRepository)
                .withConfidenceThreshold(.6)
                .withKnowledgeTypeClassifier(new RelationBasedKnowledgeTypeClassifier(relations));
    }
}
