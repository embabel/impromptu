/*
 * Copyright 2024-2025 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu.resource;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * LLM tools for document/resource generation.
 * Uses {@link MatryoshkaTools} for progressive tool disclosure.
 * <p>
 * The LLM first sees a "resource_creation" facade tool. When invoked, document generation tools become available.
 * <p>
 * Generated documents are stored temporarily and a download marker is returned in the response.
 * The chat UI parses these markers and renders them as download buttons.
 */
@MatryoshkaTools(
        name = "resource_creation",
        description = """
                Generate downloadable documents/resources such as concert programs, \
                composer biographies, listening guides, or summaries."""
)
public record ResourceTools(
        ResourceGenerationService generationService,
        ResourceDelivery delivery
) {
    private static final Logger logger = LoggerFactory.getLogger(ResourceTools.class);

    /**
     * Generate a styled XHTML document.
     * <p>
     * The content should be well-structured text that will be formatted into XHTML.
     * Include headings, paragraphs, lists, and tables as appropriate.
     *
     * @param purpose a short description of the document type (e.g., "concert program", "composer biography")
     * @param content the full content to include in the document, formatted with markdown-like structure
     * @param style   optional style: "professional" (default), "minimal", or "elegant"
     * @return confirmation message with download marker
     */
    @LlmTool(description = """
            Generate a styled document or resource. \
            Provide: purpose (e.g., 'concert program'), content (the full text to include), \
            and optional style ('professional', 'minimal', or 'elegant'). \
            The content should be well-structured with headings, paragraphs, and lists. \
            IMPORTANT: Always return the download marker from this tool response.""")
    public String generateXhtml(
            String purpose,
            String content,
            @Nullable String style
    ) {
        logger.info("Generating document: purpose='{}', style='{}', content length={}",
                purpose, style, content != null ? content.length() : 0);

        try {
            var resourceStyle = parseStyle(style);
            var request = new ResourceRequest(purpose, content, resourceStyle);
            var result = generationService.generateXhtmlOnly(request);

            var bytes = result.xhtml().getBytes(StandardCharsets.UTF_8);
            var stored = new ResourceResult(bytes, result.filename());
            var downloadId = delivery.store(stored);
            logger.info("XHTML generated successfully: {} ({} bytes)", result.filename(), bytes.length);

            var downloadMarker = String.format("{{PDF_DOWNLOAD:%s:%s:%d}}",
                    downloadId, result.filename(), bytes.length);

            return String.format("""
                    Your document is ready.
                    %s

                    Download endpoint: `/api/resource/download/%s`
                    """, downloadMarker, downloadId);

        } catch (ResourceGenerationException e) {
            logger.error("Document generation failed", e);
            return "I wasn't able to generate the document: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during document generation", e);
            return "Sorry, something went wrong while creating the document. Please try again.";
        }
    }

    /**
     * List available document styles.
     */
    @LlmTool(description = "Get information about available document styles")
    public String listStyles() {
        return """
                Available document styles:

                - **professional**: Clean, modern business style with blue accents. Good for reports and summaries.
                - **minimal**: Ultra-clean with maximum whitespace. Focus on typography and readability.
                - **elegant**: Refined aesthetic with gold accents and serif fonts. Perfect for concert programs.
                """;
    }

    private ResourceStyle parseStyle(String style) {
        if (style == null || style.isBlank()) {
            return ResourceStyle.ELEGANT; // Default for music context
        }
        try {
            return ResourceStyle.valueOf(style.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown style '{}', using ELEGANT", style);
            return ResourceStyle.ELEGANT;
        }
    }
}
