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

import java.io.ByteArrayOutputStream;

/**
 * Deterministic PDF renderer using Flying Saucer.
 * Takes validated XHTML and produces PDF bytes.
 */
@Component
public class PdfRenderer {

    private static final Logger logger = LoggerFactory.getLogger(PdfRenderer.class);

    /**
     * Render validated XHTML to PDF.
     *
     * @param xhtml    the validated XHTML content
     * @param filename suggested filename for the output
     * @return PDF result with bytes and filename
     * @throws PdfRenderingException if rendering fails
     */
    public PdfResult render(GeneratedXhtml xhtml, String filename) {
        if (!xhtml.valid()) {
            throw new PdfRenderingException("Cannot render invalid XHTML: " + xhtml.validationError());
        }

        logger.info("Rendering PDF: {}", filename);

        try {
            var renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml.xhtml());
            renderer.layout();

            var baos = new ByteArrayOutputStream();
            renderer.createPDF(baos);
            baos.close();

            var result = new PdfResult(baos.toByteArray(), filename);
            logger.info("PDF rendered successfully: {} bytes", result.size());
            return result;

        } catch (Exception e) {
            logger.error("PDF rendering failed", e);
            throw new PdfRenderingException("Failed to render PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Render with auto-generated filename based on timestamp.
     */
    public PdfResult render(GeneratedXhtml xhtml) {
        var filename = "document-" + System.currentTimeMillis() + ".pdf";
        return render(xhtml, filename);
    }
}
