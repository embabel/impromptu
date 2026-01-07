package com.embabel.impromptu.event;

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
    public final LastAnalysis lastAnalysis;

    /**
     * Last analysis
     *
     * @param messageCount Number of messages analyzed in the last analysis
     *                     (null if no prior analysis)
     */
    public record LastAnalysis(
            Integer messageCount
    ) {

        public static final LastAnalysis NONE = new LastAnalysis(null);
    }

    public ConversationAnalysisRequestEvent(
            Object source,
            ImpromptuUser user,
            Conversation conversation,
            LastAnalysis lastAnalysis) {
        super(source);
        this.user = user;
        this.conversation = conversation;
        this.lastAnalysis = lastAnalysis;
    }
}
