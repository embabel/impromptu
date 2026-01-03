package com.embabel.impromptu.chat;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.dice.projection.memory.MemoryProjection;
import com.embabel.dice.projection.memory.MemoryScope;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.proposition.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.spotify.SpotifyService;
import com.embabel.impromptu.spotify.SpotifyTools;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

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
    private final MemoryProjection memoryProjection;
    private final ApplicationEventPublisher eventPublisher;

    public ChatActions(
            SearchOperations searchOperations,
            SpotifyService spotifyService,
            MemoryProjection memoryProjection,
            ApplicationEventPublisher eventPublisher,
            ImpromptuProperties properties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "The music criticism written by Robert Schumann: His own writings",
                searchOperations)
                .withHint(TryHyDE.usingConversationContext());
        this.spotifyService = spotifyService;
        this.memoryProjection = memoryProjection;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

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

    @Action(
            canRerun = true,
            trigger = UserMessage.class
    )
    void respond(
            Conversation conversation,
            ImpromptuUser user,
            ActionContext context) {
        logger.info("ChatActions.respond() called! Conversation has {} messages",
                conversation != null ? conversation.getMessages().size() : "null");
        List<Object> tools = new LinkedList<>();
        if (user.isSpotifyLinked()) {
            tools.add(new SpotifyTools(user, spotifyService));
        }
        var userProfile = memoryProjection.projectUserProfile(
                user.getId(), MemoryScope.global(user.getId())
        );
        var assistantMessage = context.
                ai()
                .withLlm(properties.chatLlm())
                .withPromptElements(user, userProfile)
                .withReference(toolishRag)
                .withToolObjects(tools)
                .withTemplate("impromptu_chat_response")
                .respondWithSystemPrompt(conversation, Map.of(
                        "properties", properties,
                        "voice", properties.voice(),
                        "objective", properties.objective()
                ));
        context.sendMessage(conversation.addMessage(assistantMessage));

        // Publish event for async proposition extraction (every 3rd exchange)
        if (conversation.getMessages().size() % 3 == 0) {
            eventPublisher.publishEvent(new ConversationAnalysisRequestEvent(
                    this,
                    user,
                    conversation));
        }
    }
}
