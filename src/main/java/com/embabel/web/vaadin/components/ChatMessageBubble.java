package com.embabel.web.vaadin.components;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.commonmark.node.Link;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
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

    /**
     * AttributeProvider that adds target="_blank" to all links so they open
     * in a new tab and bypass Vaadin's router interception.
     */
    private static final AttributeProvider LINK_TARGET_PROVIDER = (node, tagName, attributes) -> {
        if (node instanceof Link) {
            attributes.put("target", "_blank");
            attributes.put("rel", "noopener noreferrer");
        }
    };

    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .attributeProviderFactory(context -> LINK_TARGET_PROVIDER)
            .build();

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
            // Render content with PDF download buttons as proper Vaadin Anchor components
            renderAssistantContent(contentDiv, text);
            messageDiv.add(senderSpan, contentDiv);
        }

        add(messageDiv);
    }

    /**
     * Render assistant content, extracting PDF markers as Vaadin Anchor components.
     */
    private static void renderAssistantContent(Div container, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        // First render markdown
        var document = MARKDOWN_PARSER.parse(text.strip());
        var html = HTML_RENDERER.render(document).strip();

        // Find all PDF markers and split content
        var matcher = PDF_DOWNLOAD_PATTERN.matcher(html);
        var segments = new ArrayList<Object>(); // Either String (html) or PdfDownloadInfo
        int lastEnd = 0;

        while (matcher.find()) {
            // Add text before this marker
            if (matcher.start() > lastEnd) {
                segments.add(html.substring(lastEnd, matcher.start()));
            }

            // Add the download info
            segments.add(new PdfDownloadInfo(
                    matcher.group(1), // id
                    matcher.group(2), // filename
                    Long.parseLong(matcher.group(3)) // size
            ));

            lastEnd = matcher.end();
        }

        // Add remaining text after last marker
        if (lastEnd < html.length()) {
            segments.add(html.substring(lastEnd));
        }

        // If no markers found, just render as HTML
        if (segments.isEmpty()) {
            container.add(new Html("<div>" + html + "</div>"));
            return;
        }

        // Render each segment
        for (var segment : segments) {
            if (segment instanceof String htmlSegment) {
                if (!htmlSegment.isBlank()) {
                    container.add(new Html("<span>" + htmlSegment + "</span>"));
                }
            } else if (segment instanceof PdfDownloadInfo info) {
                container.add(createPdfDownloadButton(info));
            }
        }
    }

    /**
     * Create a Vaadin Anchor component for PDF download.
     * Uses absolute URL to bypass Vaadin router completely.
     */
    private static Anchor createPdfDownloadButton(PdfDownloadInfo info) {
        // Use absolute URL to bypass Vaadin's routing - it intercepts relative URLs
        var downloadUrl = "http://127.0.0.1:8080/api/pdf/download/" + info.id;

        var anchor = new Anchor(downloadUrl, "");
        anchor.addClassName("pdf-download-btn");
        anchor.setTarget("_blank"); // Open in new tab to trigger download

        // Add icon and text as inner content
        var icon = new Span("\uD83D\uDCC4"); // 📄
        icon.addClassName("pdf-icon");

        var text = new Span(" Download " + info.filename + " ");

        var size = new Span("(" + formatFileSize(info.size) + ")");
        size.addClassName("pdf-size");

        anchor.add(icon, text, size);

        return anchor;
    }

    private record PdfDownloadInfo(String id, String filename, long size) {}

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
