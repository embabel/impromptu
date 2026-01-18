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
    private static final Pattern PDF_MARKDOWN_LINK_PATTERN =
            Pattern.compile("\\[[^\\]]+\\]\\((?:sandbox:)?/api/pdf/download/([A-Fa-f0-9\\-]{36})\\)");
    private static final Pattern PDF_ENDPOINT_PATTERN =
            Pattern.compile("/api/pdf/download/([A-Fa-f0-9\\-]{36})");

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

        var normalized = normalizePdfLinks(text);

        // Find all PDF markers in raw text and split content
        var matcher = PDF_DOWNLOAD_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            renderWithEndpointFallback(container, normalized);
            return;
        }
        matcher.reset();

        var segments = new ArrayList<Object>(); // Either String (markdown) or PdfDownloadInfo
        int lastEnd = 0;

        while (matcher.find()) {
            // Add text before this marker
            if (matcher.start() > lastEnd) {
                segments.add(normalized.substring(lastEnd, matcher.start()));
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
        if (lastEnd < normalized.length()) {
            segments.add(normalized.substring(lastEnd));
        }

        // If no markers found, just render as HTML
        if (segments.isEmpty()) {
            renderMarkdownSegment(container, text);
            return;
        }

        // Render each segment
        for (var segment : segments) {
            if (segment instanceof String markdownSegment) {
                if (!markdownSegment.isBlank()) {
                    renderMarkdownSegment(container, markdownSegment);
                }
            } else if (segment instanceof PdfDownloadInfo info) {
                container.add(createPdfDownloadButton(info));
            }
        }
    }

    private static void renderWithEndpointFallback(Div container, String text) {
        var matcher = PDF_ENDPOINT_PATTERN.matcher(text);
        var segments = new ArrayList<Object>(); // Either String (markdown) or PdfDownloadInfo
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                segments.add(text.substring(lastEnd, matcher.start()));
            }

            segments.add(new PdfDownloadInfo(
                    matcher.group(1),
                    "PDF",
                    0L
            ));

            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            segments.add(text.substring(lastEnd));
        }

        if (segments.isEmpty()) {
            renderMarkdownSegment(container, text);
            return;
        }

        for (var segment : segments) {
            if (segment instanceof String markdownSegment) {
                if (!markdownSegment.isBlank()) {
                    renderMarkdownSegment(container, markdownSegment);
                }
            } else if (segment instanceof PdfDownloadInfo info) {
                container.add(createPdfDownloadButton(info));
            }
        }
    }

    private static String normalizePdfLinks(String text) {
        var matcher = PDF_MARKDOWN_LINK_PATTERN.matcher(text);
        var sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                sb.append(text, lastEnd, matcher.start());
            }

            sb.append("{{PDF_DOWNLOAD:")
                    .append(matcher.group(1))
                    .append(":PDF:0}}");
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            sb.append(text.substring(lastEnd));
        }

        return sb.toString();
    }

    private static void renderMarkdownSegment(Div container, String markdown) {
        var document = MARKDOWN_PARSER.parse(markdown.strip());
        var html = HTML_RENDERER.render(document).strip();
        container.add(new Html("<div>" + html + "</div>"));
    }

    /**
     * Create a Vaadin Anchor component for PDF download.
     * Uses absolute URL to bypass Vaadin router completely.
     */
    private static Anchor createPdfDownloadButton(PdfDownloadInfo info) {
        var downloadUrl = "/api/pdf/download/" + info.id;

        var anchor = new Anchor(downloadUrl, "");
        anchor.addClassName("pdf-download-btn");
        anchor.getElement().setAttribute("router-ignore", true);
        anchor.setTarget("_blank"); // Open in new tab to trigger download

        // Add icon and text as inner content
        var icon = new Span("\uD83D\uDCC4"); // 📄
        icon.addClassName("pdf-icon");

        var text = new Span(" Download " + info.filename + " ");

        Span size = null;
        if (info.size > 0) {
            size = new Span("(" + formatFileSize(info.size) + ")");
            size.addClassName("pdf-size");
        }

        if (size != null) {
            anchor.add(icon, text, size);
        } else {
            anchor.add(icon, text);
        }

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
