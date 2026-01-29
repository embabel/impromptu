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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deterministic PDF renderer using Flying Saucer.
 * Takes validated XHTML and produces PDF bytes.
 */
@Component
public class XhtmlRenderer {

    private static final Logger logger = LoggerFactory.getLogger(XhtmlRenderer.class);
    private static final Path XHTML_OUTPUT_DIR = Path.of(System.getProperty("user.home"), "impromptu-resources");

    /**
     * Render validated XHTML to PDF.
     *
     * @param xhtml    the validated XHTML content
     * @param filename suggested filename for the output
     * @return resource result with bytes and filename
     * @throws ResourceGenerationException if rendering fails
     */
    public ResourceResult render(GeneratedXhtml xhtml, String filename) {
        if (!xhtml.valid()) {
            throw new ResourceGenerationException("Cannot render invalid XHTML: " + xhtml.validationError());
        }

        logger.info("Rendering resource: {}", filename);
        writeXhtmlDebugCopy(xhtml.xhtml(), filename);

        try {
            var renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml.xhtml());
            renderer.layout();

            var baos = new ByteArrayOutputStream();
            renderer.createPDF(baos);
            baos.close();

            var result = new ResourceResult(baos.toByteArray(), filename);
            logger.info("Resource rendered successfully: {} bytes", result.size());
            return result;

        } catch (Exception e) {
            logger.error("Resource rendering failed", e);
            throw new ResourceGenerationException("Failed to render resource: " + e.getMessage(), e);
        }
    }

    /**
     * Render with auto-generated filename based on timestamp.
     */
    public ResourceResult render(GeneratedXhtml xhtml) {
        var filename = "document-" + System.currentTimeMillis() + ".pdf";
        return render(xhtml, filename);
    }

    private void writeXhtmlDebugCopy(String xhtml, String filename) {
        var xhtmlFilename = filename.replaceAll("(?i)\\.pdf$", "") + ".xhtml";
        var xhtmlPath = XHTML_OUTPUT_DIR.resolve(xhtmlFilename);
        try {
            Files.createDirectories(XHTML_OUTPUT_DIR);
            Files.writeString(xhtmlPath, xhtml, StandardCharsets.UTF_8);
            logger.info("Saved XHTML debug copy: {}", xhtmlPath.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to write XHTML debug copy to {}", xhtmlPath.toAbsolutePath(), e);
        }
    }
}
