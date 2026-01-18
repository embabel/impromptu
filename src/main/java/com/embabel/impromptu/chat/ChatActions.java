package com.embabel.impromptu.chat;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.ToolObject;
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
import com.embabel.impromptu.spotify.SpotifyService;
import com.embabel.impromptu.spotify.SpotifyTools;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.impromptu.youtube.YouTubePendingPlayback;
import com.embabel.impromptu.youtube.YouTubeService;
import com.embabel.impromptu.youtube.YouTubeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;

import java.util.LinkedList;
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

    public ChatActions(
            SearchOperations searchOperations,
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            YouTubePendingPlayback youTubePendingPlayback,
            MemoryProjector memoryProjector,
            PropositionRepository propositionRepository,
            ApplicationEventPublisher eventPublisher,
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
    }

    /**
     * Bind user to AgentProcess. Will run once at the start of the process.
     * Also record that there has been a null last analysis
     */
    @Action
    ImpromptuUser bindUser(OperationContext context) {
        context.addObject(ConversationAnalysisRequestEvent.LastAnalysis.NONE);
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
     * Returns AnalyzedAt if it requests proposition analysis.
     * This will record the window
     */
    @Action(
            canRerun = true,
            trigger = UserMessage.class
    )
    void respond(
            @NonNull Conversation conversation,
            ImpromptuUser user,
            ConversationAnalysisRequestEvent.LastAnalysis lastAnalysis,
            ActionContext context) {
        var tools = new LinkedList<ToolObject>();
        if (user.isSpotifyLinked()) {
            tools.add(new ToolObject(new SpotifyTools(user, spotifyService)));
        }
        if (youTubeService.isConfigured()) {
            tools.add(new ToolObject(new YouTubeTools(user, youTubeService, youTubePendingPlayback)));
        }
        var memory = Memory.forContext(user.currentContext())
                .withRepository(propositionRepository)
                .withProjector(memoryProjector);

        var assistantMessage = context.
                ai()
                .withLlm(properties.chatLlm())
                .withId("chat_response")
                .withPromptElements(user)
                .withReferences(toolishRag, memory)
                .withToolObjects(tools)
                .withToolGroup(CoreToolGroups.WEB)
                .withTemplate("impromptu_chat_response")
                .respondWithSystemPrompt(
                        conversation.last(impromptuProperties.conversationWindow()),
                        Map.of(
                                "properties", properties,
                                "user", user
                        ));
        context.sendMessage(conversation.addMessage(assistantMessage));
    }

    @Condition
    boolean shouldAnalyze(Conversation conversation, ConversationAnalysisRequestEvent.LastAnalysis lastAnalysis) {
        var triggerInterval = properties.extraction().triggerInterval();
        if (triggerInterval > 0) {
            int lastAnalyzedAt = lastAnalysis.messageCount() != null ? lastAnalysis.messageCount() : 0;
            int currentCount = conversation.getMessages().size();
            return currentCount - lastAnalyzedAt >= triggerInterval;
        }
        return false;
    }

    @Action(
            canRerun = true,
            pre = "shouldAnalyze"
    )
    ConversationAnalysisRequestEvent.LastAnalysis analyze(Conversation conversation, ImpromptuUser user, ConversationAnalysisRequestEvent.LastAnalysis lastAnalysis) {
        eventPublisher.publishEvent(
                new ConversationAnalysisRequestEvent(
                        this,
                        user,
                        conversation,
                        lastAnalysis));
        return new ConversationAnalysisRequestEvent.LastAnalysis(conversation.getMessages().size());
    }

}
