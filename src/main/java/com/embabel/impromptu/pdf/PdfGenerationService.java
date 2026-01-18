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

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.AiBuilder;
import com.embabel.impromptu.ImpromptuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * High-level service for PDF generation.
 * Orchestrates the XHTML generation, validation, and rendering flow.
 * <p>
 * This service can be used directly without going through the action framework
 * for simpler use cases.
 */
@Service
public class PdfGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationService.class);
    private static final int MAX_FIX_ATTEMPTS = 2;

    private final Ai ai;
    private final XhtmlValidator validator;
    private final PdfRenderer renderer;
    private final ImpromptuProperties properties;

    public PdfGenerationService(AiBuilder aiBuilder,
                                ImpromptuProperties properties,
                                XhtmlValidator validator, PdfRenderer renderer) {
        this.ai = aiBuilder.ai();
        this.properties = properties;
        this.validator = validator;
        this.renderer = renderer;
    }

    /**
     * Generate a PDF from a request.
     * Handles the full flow: XHTML generation → validation → fix loop → render.
     *
     * @param request the PDF request with content and style
     * @return the rendered PDF result
     * @throws PdfRenderingException if generation fails after all retry attempts
     */
    public PdfResult generate(PdfRequest request) {
        logger.info("Starting PDF generation for purpose: {}", request.purpose());

        // Step 1: Generate initial XHTML
        var xhtml = generateXhtml(request);

        // Step 2: Validate and fix loop
        var validated = validateAndFix(xhtml, request);

        // Step 3: Render to PDF
        var filename = generateFilename(request.purpose());
        var result = renderer.render(validated, filename);

        logger.info("PDF generation complete: {} ({} bytes)", filename, result.size());
        return result;
    }

    /**
     * Generate validated XHTML only (no PDF rendering).
     */
    public XhtmlResult generateXhtmlOnly(PdfRequest request) {
        logger.info("Starting XHTML generation for purpose: {}", request.purpose());

        // Step 1: Generate initial XHTML
        var xhtml = generateXhtml(request);

        // Step 2: Validate and fix loop
        var validated = validateAndFix(xhtml, request);

        var filename = generateFilename(request.purpose()).replaceAll("(?i)\\.pdf$", "") + ".xhtml";
        return new XhtmlResult(validated.xhtml(), filename);
    }

    /**
     * Generate a PDF from raw content with default style.
     */
    public PdfResult generate(String purpose, String content) {
        return generate(new PdfRequest(purpose, content));
    }

    /**
     * Generate XHTML from request using LLM.
     */
    private GeneratedXhtml generateXhtml(PdfRequest request) {
        var style = request.effectiveStyle();

        var response = ai
                .withLlm(properties.pdfGenerationLlm())
                .withId("pdf_generate_xhtml")
                .withTemplate("pdf_generate_xhtml")
                .generateText(
                        Map.of(
                                "purpose", request.purpose(),
                                "content", request.content(),
                                "styleName", style.name(),
                                "styleDescription", style.getDescription(),
                                "css", style.getCss()
                        )
                );

        return GeneratedXhtml.initial(extractXhtml(response));
    }

    /**
     * Validate XHTML and fix if needed, with retry loop.
     */
    private GeneratedXhtml validateAndFix(GeneratedXhtml xhtml, PdfRequest request) {
        var current = xhtml;

        while (current.fixAttempts() <= MAX_FIX_ATTEMPTS) {
            var validated = validator.validate(current);

            if (validated.valid()) {
                return validated;
            }

            if (current.fixAttempts() >= MAX_FIX_ATTEMPTS) {
                throw new PdfRenderingException(
                        "Failed to generate valid XHTML after " + MAX_FIX_ATTEMPTS +
                                " fix attempts. Last error: " + validated.validationError());
            }

            logger.warn("XHTML validation failed (attempt {}): {}",
                    current.fixAttempts() + 1, validated.validationError());

            // Try to fix
            current = fixXhtml(validated);
        }

        throw new PdfRenderingException("Exceeded maximum fix attempts");
    }

    /**
     * Attempt to fix invalid XHTML using LLM.
     */
    private GeneratedXhtml fixXhtml(GeneratedXhtml invalid) {
        var response = ai
                .withLlm(properties.pdfGenerationLlm().withTemperature(0.0))
                .withTemplate("pdf_fix_xhtml")
                .generateText(
                        Map.of(
                                "xhtml", invalid.xhtml(),
                                "error", invalid.validationError() != null ? invalid.validationError() : "Unknown error",
                                "attempt", invalid.fixAttempts() + 1
                        )
                );

        return invalid.withFixedXhtml(extractXhtml(response));
    }

    /**
     * Extract XHTML from LLM response, handling markdown code blocks.
     */
    private String extractXhtml(String response) {
        if (response == null) {
            return "";
        }

        var trimmed = response.trim();

        // Handle markdown code blocks
        if (trimmed.startsWith("```")) {
            var start = trimmed.indexOf('\n') + 1;
            var end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }

        return trimmed;
    }

    /**
     * Generate filename from purpose.
     */
    private String generateFilename(String purpose) {
        var sanitized = purpose.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (sanitized.isEmpty()) {
            sanitized = "document";
        }
        return sanitized + "-" + System.currentTimeMillis() + ".pdf";
    }
}
