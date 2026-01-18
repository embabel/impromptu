/*
 * Copyright 2024-2025 Embabel Software, Inc.
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
package com.embabel.impromptu.pdf;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * LLM tools for PDF generation.
 * Uses {@link MatryoshkaTools} for progressive tool disclosure.
 * <p>
 * The LLM first sees a "pdf" facade tool. When invoked, PDF generation tools become available.
 * <p>
 * Generated PDFs are stored temporarily and a download marker is returned in the response.
 * The chat UI parses these markers and renders them as download buttons.
 */
@MatryoshkaTools(
        name = "pdf",
        description = "Generate PDF documents. Invoke this tool to create professional PDFs " +
                "such as concert programs, composer biographies, listening guides, or summaries."
)
public record PdfTools(
        PdfGenerationService pdfService,
        PdfDelivery delivery
) {
    private static final Logger logger = LoggerFactory.getLogger(PdfTools.class);

    /**
     * Generate a styled PDF document.
     * <p>
     * The content should be well-structured text that will be formatted into XHTML.
     * Include headings, paragraphs, lists, and tables as appropriate.
     *
     * @param purpose a short description of the document type (e.g., "concert program", "composer biography")
     * @param content the full content to include in the PDF, formatted with markdown-like structure
     * @param style   optional style: "professional" (default), "minimal", or "elegant"
     * @return confirmation message with download marker
     */
    @LlmTool(description = "Generate a styled PDF document. " +
            "Provide: purpose (e.g., 'concert program'), content (the full text to include), " +
            "and optional style ('professional', 'minimal', or 'elegant'). " +
            "The content should be well-structured with headings, paragraphs, and lists.")
    public String generatePdf(
            String purpose,
            String content,
            @Nullable String style
    ) {
        logger.info("Generating PDF: purpose='{}', style='{}', content length={}",
                purpose, style, content != null ? content.length() : 0);

        try {
            var pdfStyle = parseStyle(style);
            var request = new PdfRequest(purpose, content, pdfStyle);
            var result = pdfService.generate(request);

            var downloadId = delivery.store(result);
            logger.info("PDF generated successfully: {} ({} bytes)", result.filename(), result.size());

            // Return message with direct download link
            return String.format("I've created your %s.\n\n[Download %s](/api/pdf/download/%s) (%s)",
                    purpose,
                    result.filename(),
                    downloadId,
                    formatFileSize(result.size()));

        } catch (PdfRenderingException e) {
            logger.error("PDF generation failed", e);
            return "I wasn't able to generate the PDF: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during PDF generation", e);
            return "Sorry, something went wrong while creating the PDF. Please try again.";
        }
    }

    /**
     * List available PDF styles.
     */
    @LlmTool(description = "Get information about available PDF styles")
    public String listStyles() {
        return """
                Available PDF styles:
                
                - **professional**: Clean, modern business style with blue accents. Good for reports and summaries.
                - **minimal**: Ultra-clean with maximum whitespace. Focus on typography and readability.
                - **elegant**: Refined aesthetic with gold accents and serif fonts. Perfect for concert programs.
                """;
    }

    private PdfStyle parseStyle(String style) {
        if (style == null || style.isBlank()) {
            return PdfStyle.ELEGANT; // Default for music context
        }
        try {
            return PdfStyle.valueOf(style.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown style '{}', using ELEGANT", style);
            return PdfStyle.ELEGANT;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
