package com.embabel.impromptu.proposition;

import com.embabel.chat.Conversation;
import com.embabel.impromptu.user.ImpromptuUser;
import org.springframework.context.ApplicationEvent;

/**
 * Event published after a conversation exchange (user message + assistant response).
 * Used to trigger async proposition extraction.
 */
public class ConversationAnalysisRequestEvent extends ApplicationEvent {

    public final ImpromptuUser user;
    public final Conversation conversation;

    public ConversationAnalysisRequestEvent(
            Object source,
            ImpromptuUser user,
            Conversation conversation) {
        super(source);
        this.user = user;
        this.conversation = conversation;
    }
}
