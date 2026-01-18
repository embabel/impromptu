package com.embabel.web.vaadin.components;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.regex.Pattern;

/**
 * Chat message bubble component with sender name and text content.
 * Styled differently for user vs assistant messages.
 * Assistant messages render markdown as HTML.
 * <p>
 * Special markers are parsed and rendered:
 * - {@code {{PDF_DOWNLOAD:id:filename:size}}} renders as a download button
 */
public class ChatMessageBubble extends Div {

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    /**
     * Pattern to match PDF download markers: {{PDF_DOWNLOAD:id:filename:size}}
     * Uses double braces to avoid markdown link interpretation.
     */
    private static final Pattern PDF_DOWNLOAD_PATTERN =
            Pattern.compile("\\{\\{PDF_DOWNLOAD:([^:]+):([^:]+):(\\d+)\\}\\}");

    public ChatMessageBubble(String sender, String text, boolean isUser) {
        addClassName("chat-bubble-container");
        addClassName(isUser ? "user" : "assistant");

        var messageDiv = new Div();
        messageDiv.addClassName("chat-bubble");
        messageDiv.addClassName(isUser ? "user" : "assistant");

        var senderSpan = new Span(sender);
        senderSpan.addClassName("chat-bubble-sender");

        // User messages: plain text. Assistant messages: render markdown
        if (isUser) {
            var textSpan = new Span(text);
            textSpan.addClassName("chat-bubble-text");
            messageDiv.add(senderSpan, textSpan);
        } else {
            var contentDiv = new Div();
            contentDiv.addClassName("chat-bubble-text");
            contentDiv.add(new Html("<div>" + renderMarkdown(text) + "</div>"));
            messageDiv.add(senderSpan, contentDiv);
        }

        add(messageDiv);
    }

    /**
     * Convert markdown to HTML, then process special markers.
     */
    private static String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        // First render markdown to HTML
        var document = MARKDOWN_PARSER.parse(markdown.strip());
        var html = HTML_RENDERER.render(document).strip();
        // Then process PDF download markers on the HTML output
        return processPdfDownloadMarkers(html);
    }

    /**
     * Replace PDF download markers with HTML download buttons.
     */
    private static String processPdfDownloadMarkers(String text) {
        var matcher = PDF_DOWNLOAD_PATTERN.matcher(text);
        var result = new StringBuilder();

        while (matcher.find()) {
            var id = matcher.group(1);
            var filename = matcher.group(2);
            var sizeBytes = Long.parseLong(matcher.group(3));
            var sizeDisplay = formatFileSize(sizeBytes);

            // Create a download link styled as a button
            var downloadHtml = String.format(
                    "<a href=\"/api/pdf/download/%s\" class=\"pdf-download-btn\" download=\"%s\">" +
                            "<span class=\"pdf-icon\">📄</span> Download %s <span class=\"pdf-size\">(%s)</span></a>",
                    escapeHtml(id),
                    escapeHtml(filename),
                    escapeHtml(filename),
                    sizeDisplay
            );
            // Use quoteReplacement to handle special chars like $ in the replacement string
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(downloadHtml));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Format file size for display.
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Escape HTML special characters.
     */
    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
