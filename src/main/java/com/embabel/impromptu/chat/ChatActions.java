package com.embabel.impromptu.chat;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.rag.neo.drivine.CypherQueryTools;
import com.embabel.agent.tools.mcp.McpToolFactory;
import com.embabel.chat.AssetTracker;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.dice.agent.Memory;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.event.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.integrations.PerformanceFinderService;
import com.embabel.impromptu.integrations.imslp.ImslpTools;
import com.embabel.impromptu.integrations.metmuseum.MetMuseumTools;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.spotify.SpotifyTools;
import com.embabel.impromptu.integrations.youtube.YouTubePendingPlayback;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.integrations.youtube.YouTubeTools;
import com.embabel.impromptu.pdf.PdfGenerationService;
import com.embabel.impromptu.pdf.ResourceDelivery;
import com.embabel.impromptu.pdf.ResourceTools;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The platform can use any action to respond to user messages.
 */
@EmbabelComponent
public record ChatActions(
        LlmReference sources,
        SpotifyService spotifyService,
        YouTubeService youTubeService,
        YouTubePendingPlayback youTubePendingPlayback,
        MemoryProjector memoryProjector,
        PropositionRepository propositionRepository,
        ApplicationEventPublisher eventPublisher,
        PdfGenerationService pdfGenerationService,
        ResourceDelivery pdfDelivery,
        PerformanceFinderService performanceFinderService,
        ImpromptuProperties properties,
        McpToolFactory mcpToolFactory,
        CypherQueryTools cypherQueryTools
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

        var assets = conversation.getAssetTracker().mostRecentlyAdded(10).references();
        logger.info("Tracking {} assets: {}",
                assets.size(),
                assets.stream().map(LlmReference::getName).toList()
        );

        var assistantMessage = context.ai()
                .withLlm(properties.chatLlm())
                .withId("chat_response")
                .withPromptElements(user)
                .withReferences(sources, memory)
                .withReferences(assets)
                .withToolObjects(toolObjectsForUser(user, conversation.getAssetTracker()))
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
        var tools = new LinkedList<>(commonTools());
        if (user.isSpotifyLinked()) {
            tools.add(new SpotifyTools(user, spotifyService));
        }
        if (youTubeService.isConfigured()) {
            tools.add(new YouTubeTools(user, youTubeService, youTubePendingPlayback));
        }
        if (performanceFinderService.isAvailable(user)) {
            tools.add(
                    assetTracker.addReturnedAssets(performanceFinderService.createPerformanceFinderTool(user, null)));
        }
        return tools;
    }

    private List<Object> commonTools() {
        return List.of(
                mcpToolFactory.requiredToolByName("brave_web_search"),
                mcpToolFactory.matryoshkaByName(
                        "wikipedia",
                        "Search and find content from Wikipedia",
                        Set.of("search_wikipedia", "get_article", "get_related_topics", "get_summary", "get_wikipedia_summary")),
                MetMuseumTools.DEFAULT,
                ImslpTools.DEFAULT,
                new ResourceTools(pdfGenerationService, pdfDelivery),
                cypherQueryTools.tool("""
                        Use this tool to query existing entities such as composers and works.
                        If you are asked questions like "Who composed the most violin concertos?" or
                        "List saxophone concertos" use this tool
                        """)
        );
    }
}
