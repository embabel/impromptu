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
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates XHTML for Flying Saucer rendering.
 * Performs both XML parsing validation and Flying Saucer layout validation.
 */
@Component
public class XhtmlValidator {

    private static final Logger logger = LoggerFactory.getLogger(XhtmlValidator.class);

    // Common Flying Saucer issues to check
    private static final List<Pattern> UNSUPPORTED_CSS = List.of(
            Pattern.compile("display\\s*:\\s*(flex|grid|inline-flex|inline-grid)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(gap|row-gap|column-gap)\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(align-items|justify-content|flex-direction)\\s*:", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Validate XHTML and return result with validation status.
     */
    public GeneratedXhtml validate(GeneratedXhtml input) {
        logger.debug("Validating XHTML ({} chars)", input.xhtml().length());

        var errors = new ArrayList<String>();

        // Step 1: Check for unsupported CSS
        for (var pattern : UNSUPPORTED_CSS) {
            if (pattern.matcher(input.xhtml()).find()) {
                errors.add("Unsupported CSS detected: " + pattern.pattern() + " - CSS 2.1 only");
            }
        }

        // Step 2: XML parsing validation
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            var stream = new ByteArrayInputStream(input.xhtml().getBytes(StandardCharsets.UTF_8));
            builder.parse(stream);
        } catch (Exception e) {
            errors.add("XML parsing error: " + simplifyError(e.getMessage()));
            return input.withError(String.join("; ", errors));
        }

        // Step 3: Flying Saucer layout validation (actually try to render)
        try {
            var renderer = new ITextRenderer();
            renderer.setDocumentFromString(input.xhtml());
            renderer.layout();

            // Try to actually create PDF to catch any late errors
            var testOutput = new ByteArrayOutputStream();
            renderer.createPDF(testOutput);
            testOutput.close();

        } catch (Exception e) {
            errors.add("Rendering error: " + simplifyError(e.getMessage()));
            return input.withError(String.join("; ", errors));
        }

        if (!errors.isEmpty()) {
            return input.withError(String.join("; ", errors));
        }

        logger.debug("XHTML validation passed");
        return input.validated();
    }

    /**
     * Check if the XHTML is valid without modifying it.
     */
    public boolean isValid(String xhtml) {
        var result = validate(GeneratedXhtml.initial(xhtml));
        return result.valid();
    }

    /**
     * Simplify verbose error messages for LLM consumption.
     */
    private String simplifyError(String error) {
        if (error == null) {
            return "Unknown error";
        }

        // Common error simplifications
        if (error.contains("Content is not allowed in prolog")) {
            return "Invalid characters before XML declaration - ensure document starts with <?xml";
        }
        if (error.contains("must be terminated by the matching end-tag")) {
            return error.replaceAll(".*element type \"([^\"]+)\".*", "Unclosed tag: <$1>");
        }
        if (error.contains("The element type") && error.contains("must be terminated")) {
            return error.replaceAll(".*\"([^\"]+)\".*", "Tag not properly closed: $1");
        }
        if (error.contains("cvc-") || error.contains("SAXParseException")) {
            // Extract just the useful part
            var idx = error.lastIndexOf(':');
            if (idx > 0 && idx < error.length() - 1) {
                return error.substring(idx + 1).trim();
            }
        }

        // Truncate long messages
        if (error.length() > 200) {
            return error.substring(0, 200) + "...";
        }

        return error;
    }
}
