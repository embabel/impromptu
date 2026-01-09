package com.embabel.web.vaadin.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Chat message bubble component with sender name and text content.
 * Styled differently for user vs assistant messages.
 */
public class ChatMessageBubble extends Div {

    public ChatMessageBubble(String sender, String text, boolean isUser) {
        addClassName("chat-bubble-container");
        addClassName(isUser ? "user" : "assistant");

        var messageDiv = new Div();
        messageDiv.addClassName("chat-bubble");
        messageDiv.addClassName(isUser ? "user" : "assistant");

        var senderSpan = new Span(sender);
        senderSpan.addClassName("chat-bubble-sender");

        var textSpan = new Span(text);
        textSpan.addClassName("chat-bubble-text");

        messageDiv.add(senderSpan, textSpan);
        add(messageDiv);
    }

    /**
     * Creates a user message bubble.
     */
    public static ChatMessageBubble user(String text) {
        return new ChatMessageBubble("You", text, true);
    }

    /**
     * Creates an assistant message bubble.
     */
    public static ChatMessageBubble assistant(String persona, String text) {
        return new ChatMessageBubble(persona, text, false);
    }

    /**
     * Creates an error message bubble.
     */
    public static Div error(String text) {
        var messageDiv = new Div();
        messageDiv.addClassName("chat-bubble-error");
        messageDiv.setText(text);
        return messageDiv;
    }
}
