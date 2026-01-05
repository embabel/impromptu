package com.embabel.impromptu.vaadin.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Chat message bubble component with sender name and text content.
 * Styled differently for user vs assistant messages.
 */
public class ChatMessageBubble extends Div {

    public ChatMessageBubble(String sender, String text, boolean isUser) {
        getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", isUser ? "flex-end" : "flex-start")
                .set("width", "100%");

        var messageDiv = new Div();
        messageDiv.getStyle()
                .set("max-width", "80%")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin", "var(--lumo-space-xs) 0");

        if (isUser) {
            messageDiv.getStyle()
                    .set("background", "var(--lumo-primary-color)")
                    .set("color", "var(--lumo-primary-contrast-color)");
        } else {
            messageDiv.getStyle()
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-body-text-color)");
        }

        var senderSpan = new Span(sender);
        senderSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        var textSpan = new Span(text);
        textSpan.getStyle()
                .set("white-space", "pre-wrap")
                .set("font-size", "var(--lumo-font-size-l)");

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
        messageDiv.getStyle()
                .set("background", "var(--lumo-error-color-10pct)")
                .set("border-left", "3px solid var(--lumo-error-color)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin", "var(--lumo-space-xs) 0");
        messageDiv.setText(text);
        return messageDiv;
    }
}
