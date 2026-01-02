package com.embabel.impromptu.chat;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.chat.Chatbot;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.dice.projection.memory.DefaultMemoryProjection;
import com.embabel.dice.projection.memory.KeywordMatchingMemoryTypeClassifier;
import com.embabel.dice.projection.memory.MemoryProjection;
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
    MemoryProjection memoryProjection(ImpromptuUserService userService,
                                      PropositionRepository propositionRepository) {
        return DefaultMemoryProjection
                .against(propositionRepository)
                .withConfidenceThreshold(.6)
                .withMemoryTypeClassifier(KeywordMatchingMemoryTypeClassifier.INSTANCE);
    }
}
