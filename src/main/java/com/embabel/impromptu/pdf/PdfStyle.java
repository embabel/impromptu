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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Pre-built CSS style templates for PDF generation.
 * Each style provides a complete CSS stylesheet optimized for Flying Saucer (CSS 2.1).
 */
public enum PdfStyle {

    /**
     * Clean, modern professional style suitable for business documents.
     */
    PROFESSIONAL("pdf/styles/professional.css"),

    /**
     * Minimal style with maximum readability.
     */
    MINIMAL("pdf/styles/minimal.css"),

    /**
     * Elegant style inspired by the Impromptu theme - concert hall aesthetic.
     */
    ELEGANT("pdf/styles/elegant.css");

    private static final Logger logger = LoggerFactory.getLogger(PdfStyle.class);

    private final String resourcePath;
    private String cachedCss;

    PdfStyle(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * Get the CSS content for this style.
     * CSS is loaded once and cached.
     */
    public synchronized String getCss() {
        if (cachedCss == null) {
            cachedCss = loadCss();
        }
        return cachedCss;
    }

    private String loadCss() {
        try {
            var resource = new ClassPathResource(resourcePath);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to load CSS from {}, using fallback", resourcePath, e);
            return getFallbackCss();
        }
    }

    /**
     * Fallback CSS if resource loading fails.
     */
    private String getFallbackCss() {
        return """
                @page {
                    size: letter;
                    margin: 1in;
                }
                body {
                    font-family: Helvetica, Arial, sans-serif;
                    font-size: 11pt;
                    line-height: 1.5;
                    color: #333;
                }
                h1 { font-size: 24pt; margin-bottom: 12pt; }
                h2 { font-size: 18pt; margin-bottom: 10pt; }
                h3 { font-size: 14pt; margin-bottom: 8pt; }
                p { margin-bottom: 10pt; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 12pt; }
                th, td { border: 1px solid #ccc; padding: 6pt 8pt; text-align: left; }
                th { background-color: #f5f5f5; font-weight: bold; }
                """;
    }

    /**
     * Get a description of this style for LLM context.
     */
    public String getDescription() {
        return switch (this) {
            case PROFESSIONAL -> "Clean, modern business style with blue accents. Suitable for reports, invoices, summaries.";
            case MINIMAL -> "Ultra-clean with maximum whitespace. Focus on typography and readability.";
            case ELEGANT -> "Refined aesthetic with gold accents and serif fonts. Inspired by concert programs.";
        };
    }
}
