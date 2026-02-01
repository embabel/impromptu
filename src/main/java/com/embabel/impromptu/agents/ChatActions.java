/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu.agents;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.tool.Subagent;
import com.embabel.agent.api.tool.Tool;
import com.embabel.chat.AssetTracker;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.dice.agent.Memory;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.agents.ProgramNoteWriter.ProgramNoteRequest;
import com.embabel.impromptu.domain.performance.Concert;
import com.embabel.impromptu.domain.performance.ConcertPlan;
import com.embabel.impromptu.event.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.integrations.coordination.ConcertAssemblyService;
import com.embabel.impromptu.integrations.coordination.ConcertPlanningService;
import com.embabel.impromptu.integrations.coordination.PerformanceAssemblyService;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubePendingPlayback;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The platform can use any action to respond to user messages.
 */
@EmbabelComponent
public record ChatActions(
        @Qualifier("sourcesTool") Tool sources,
        SpotifyService spotifyService,
        YouTubeService youTubeService,
        YouTubePendingPlayback youTubePendingPlayback,
        MemoryProjector memoryProjector,
        PropositionRepository propositionRepository,
        ApplicationEventPublisher eventPublisher,
        PerformanceAssemblyService performanceAssemblyService,
        ConcertPlanningService concertPlanningService,
        ConcertAssemblyService concertAssemblyService,
        ImpromptuProperties properties,
        ChatConfiguration.CommonTools commonTools
) {
    private static final Logger logger = LoggerFactory.getLogger(ChatActions.class);

    /**
     * Bind user to AgentProcess. Will run once at the start of the process.
     */
    @Action
    ImpromptuUser bindUser(OperationContext context) {
        var forUser = context.getProcessContext().getProcessOptions().getIdentities().getForUser();
        if (forUser instanceof ImpromptuUser iu) {
            return iu;
        } else {
            logger.warn("bindUser: forUser is not an ImpromptuUser: {}", forUser);
            return null;
        }
    }

    /**
     * Invoked for each user message in the conversation.
     */
    @Action(
            canRerun = true,
            trigger = UserMessage.class
    )
    void respond(
            @NonNull Conversation conversation,
            ImpromptuUser user,
            ActionContext context) {
        var memory = Memory.forContext(user.currentContext())
                .withRepository(propositionRepository)
                .withEagerQuery(
                        q -> q.orderedByEffectiveConfidence()
//                                .createdSince(Duration.ofDays(10))
                                .withLimit(10)
                )
                .withProjector(memoryProjector);

        var assetTracker = conversation.getAssetTracker();
        var assets = assetTracker.mostRecentlyAdded(10).references();
        logger.info("Tracking {} assets: {}",
                assets.size(),
                assets.stream().map(LlmReference::getName).toList()
        );

        // Build tools list - conditionally include ProgramNoteWriter when Concert is available
        var tools = new LinkedList<Tool>();
        tools.add(memory);
        tools.add(sources);
        tools.add(assetTracker.addReturnedAssets(
                Subagent.ofClass(ConcertAssembler.class).consuming(ConcertPlan.class)));

        // If there's a Concert in the assets, expose ProgramNoteWriter
        // Put the Concert in the blackboard so the subagent can access it
        var concert = assetTracker.getAssets().stream()
                .filter(Concert.class::isInstance)
                .map(Concert.class::cast)
                .reduce((first, second) -> second)  // Get the most recent
                .orElse(null);
        if (concert != null) {
            logger.info("Concert found in assets, adding to blackboard and exposing ProgramNoteWriter");
            context.bind("concert", concert);
            tools.add(assetTracker.addReturnedAssets(
                    Subagent.ofClass(ProgramNoteWriter.class).consuming(ProgramNoteRequest.class)));
        }

        var assistantMessage = context.ai()
                .withLlm(properties.chatLlm())
                .withId("chat_response")
                .withPromptElements(user)
                .withReferences(assets)
                .withTools(tools)
                .withToolObjects(toolObjectsForUser(user, assetTracker))
                .rendering("impromptu_chat_response")
                .respondWithSystemPrompt(
                        conversation.last(properties.conversationWindow()),
                        Map.of(
                                "properties", properties,
                                "user", user
                        ));
        context.sendAndSave(assistantMessage);

        // Always request analysis - IncrementalAnalyzer decides if ready
        eventPublisher.publishEvent(new ConversationAnalysisRequestEvent(this, user, conversation));
    }

    /**
     * Get tool objects (classes with @LlmTool methods) for the user.
     */
    private List<Object> toolObjectsForUser(ImpromptuUser user, AssetTracker assetTracker) {
        var tools = new LinkedList<>(commonTools.tools());
//        if (user.isSpotifyLinked()) {
//            tools.add(new SpotifyTools(user, spotifyService));
//        }
//        if (youTubeService.isConfigured()) {
//            tools.add(new YouTubeTools(user, youTubeService, youTubePendingPlayback));
//        }
//        if (performanceAssemblyService.isAvailable(user)) {
//            tools.add(
//                    assetTracker.addReturnedAssets(performanceAssemblyService.createPerformanceFinderTool(user)));
//        }
        // Planning tool is always available (no platform dependencies)
        tools.add(assetTracker.addReturnedAssets(concertPlanningService.createConcertPlanningTool()));
        // AgenticTool version - commented out in favor of ConcertAssembler subagent
        // if (concertAssemblyService.isAvailable(user)) {
        //     tools.add(
        //             assetTracker.addReturnedAssets(concertAssemblyService.createConcertAssemblyTool(user)));
        // }
        return tools;
    }
}
