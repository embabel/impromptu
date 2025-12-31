package com.embabel.impromptu;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;

import java.util.Map;

/**
 * The platform can use any action to respond to user messages.
 */
@EmbabelComponent
public class ChatActions {

    private final ToolishRag toolishRag;
    private final ImpromptuProperties properties;

    public ChatActions(
            SearchOperations searchOperations,
            ImpromptuProperties properties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "The music criticism written by Robert Schumann: His own writings",
                searchOperations)
                .withHint(TryHyDE.usingConversationContext());
        this.properties = properties;
    }

    // TODO orient

    @Action(
            canRerun = true,
            trigger = UserMessage.class
    )
    void respond(
            Conversation conversation,
            ActionContext context) {
        // We could use a simple prompt here but choose to use a template
        // as chatbots tend to require longer prompts
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
    }
}
