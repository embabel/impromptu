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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for downloading generated PDFs.
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(PdfDownloadController.class);

    private final PdfDelivery delivery;

    public PdfDownloadController(PdfDelivery delivery) {
        this.delivery = delivery;
    }

    /**
     * Download a generated PDF by its ID.
     * The PDF remains available for multiple downloads until it expires.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        logger.info("PDF download requested: {}", id);

        var result = delivery.retrieve(id);
        if (result.isEmpty()) {
            logger.warn("PDF not found or expired: {}", id);
            return ResponseEntity.notFound().build();
        }

        var pdf = result.get();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", pdf.filename());
        headers.setContentLength(pdf.size());

        logger.info("Serving PDF: {} ({} bytes)", pdf.filename(), pdf.size());
        return new ResponseEntity<>(pdf.pdfBytes(), headers, HttpStatus.OK);
    }

    /**
     * Check if a PDF is available for download.
     */
    @GetMapping("/exists/{id}")
    public ResponseEntity<Void> exists(@PathVariable String id) {
        if (delivery.exists(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
