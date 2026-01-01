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
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.proposition.ConversationExchangeEvent;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * The platform can use any action to respond to user messages.
 */
@EmbabelComponent
public class ChatActions {

    private static final Logger logger = LoggerFactory.getLogger(ChatActions.class);

    private final ToolishRag toolishRag;
    private final ImpromptuProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public ChatActions(
            SearchOperations searchOperations,
            ApplicationEventPublisher eventPublisher,
            ImpromptuProperties properties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "The music criticism written by Robert Schumann: His own writings",
                searchOperations)
                .withHint(TryHyDE.usingConversationContext());
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    // TODO orient

    @Action(
            canRerun = true,
            trigger = UserMessage.class
    )
    void respond(
            Conversation conversation,
            ActionContext context) {
        logger.info("ChatActions.respond() called! Conversation has {} messages",
                conversation != null ? conversation.getMessages().size() : "null");
        var assistantMessage = context.
                ai()
                .withLlm(properties.chatLlm())
                .withReference(toolishRag)
                .withTemplate("ragbot")
                .respondWithSystemPrompt(conversation, Map.of(
                        "properties", properties,
                        "voice", properties.voice(),
                        "objective", properties.objective()
                ));
        context.sendMessage(conversation.addMessage(assistantMessage));

        var user = userFromContext(context);
        // Publish event for async proposition extraction (every 3rd exchange)
        if (user != null && conversation.getMessages().size() % 3 == 0) {
            eventPublisher.publishEvent(new ConversationExchangeEvent(
                    this,
                    user,
                    conversation));
        }
    }

    @Nullable
    private ImpromptuUser userFromContext(OperationContext context) {
        var forUser = context.getProcessContext().getProcessOptions().getIdentities().getForUser();
        return forUser instanceof ImpromptuUser iu ? iu : null;
    }
}
