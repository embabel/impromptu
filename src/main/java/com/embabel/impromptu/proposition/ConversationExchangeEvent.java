package com.embabel.impromptu.proposition;

import com.embabel.chat.Conversation;
import org.springframework.context.ApplicationEvent;

/**
 * Event published after a conversation exchange (user message + assistant response).
 * Used to trigger async proposition extraction.
 */
public class ConversationExchangeEvent extends ApplicationEvent {

    private final Conversation conversation;

    public ConversationExchangeEvent(Object source, Conversation conversation) {
        super(source);
        this.conversation = conversation;
    }

    public Conversation getConversation() {
        return conversation;
    }
}
