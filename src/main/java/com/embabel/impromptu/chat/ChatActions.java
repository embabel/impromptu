package com.embabel.impromptu.chat;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.dice.agent.Memory;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.event.ConversationAnalysisRequestEvent;
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

/**
 * The platform can use any action to respond to user messages.
 */
@EmbabelComponent
public class ChatActions {

    private static final Logger logger = LoggerFactory.getLogger(ChatActions.class);

    private final ToolishRag toolishRag;
    private final ImpromptuProperties properties;
    private final SpotifyService spotifyService;
    private final YouTubeService youTubeService;
    private final YouTubePendingPlayback youTubePendingPlayback;
    private final MemoryProjector memoryProjector;
    private final PropositionRepository propositionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ImpromptuProperties impromptuProperties;
    private final PdfGenerationService pdfGenerationService;
    private final ResourceDelivery pdfDelivery;

    public ChatActions(
            SearchOperations searchOperations,
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            YouTubePendingPlayback youTubePendingPlayback,
            MemoryProjector memoryProjector,
            PropositionRepository propositionRepository,
            ApplicationEventPublisher eventPublisher,
            PdfGenerationService pdfGenerationService,
            ResourceDelivery pdfDelivery,
            ImpromptuProperties properties, ImpromptuProperties impromptuProperties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "The music criticism written by Robert Schumann: His own writings",
                searchOperations)
                .withHint(TryHyDE.usingConversationContext());
        this.spotifyService = spotifyService;
        this.youTubeService = youTubeService;
        this.youTubePendingPlayback = youTubePendingPlayback;
        this.propositionRepository = propositionRepository;
        this.memoryProjector = memoryProjector;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.impromptuProperties = impromptuProperties;
        this.pdfGenerationService = pdfGenerationService;
        this.pdfDelivery = pdfDelivery;
    }

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
                .withProjector(memoryProjector);

        var assistantMessage = context.
                ai()
                .withLlm(properties.chatLlm())
                .withId("chat_response")
                .withPromptElements(user)
                .withReferences(toolishRag, memory)
                .withToolObjects(toolInstancesForUser(user))
                .withToolGroup(CoreToolGroups.WEB)
                .withTemplate("impromptu_chat_response")
                .respondWithSystemPrompt(
                        conversation.last(impromptuProperties.conversationWindow()),
                        Map.of(
                                "properties", properties,
                                "user", user
                        ));
        context.sendMessage(conversation.addMessage(assistantMessage));

        // Always request analysis - IncrementalAnalyzer decides if ready
        eventPublisher.publishEvent(new ConversationAnalysisRequestEvent(this, user, conversation));
    }

    private List<Object> toolInstancesForUser(ImpromptuUser user) {
        var tools = new LinkedList<>();
        if (user.isSpotifyLinked()) {
            tools.add(new SpotifyTools(user, spotifyService));
        }
        if (youTubeService.isConfigured()) {
            tools.add(new YouTubeTools(user, youTubeService, youTubePendingPlayback));
        }
        tools.add(MetMuseumTools.DEFAULT);
        tools.add(ImslpTools.DEFAULT);
        tools.add(new ResourceTools(pdfGenerationService, pdfDelivery));
        return tools;
    }

}
