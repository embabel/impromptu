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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDF storage strategy that writes files to the local filesystem.
 * Default implementation; activated when pdf.storage=filesystem or not set.
 */
@Component
@ConditionalOnProperty(name = "pdf.storage", havingValue = "filesystem", matchIfMissing = true)
public class LocalFilesystemPdfStorage implements PdfStorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalFilesystemPdfStorage.class);
    private static final Path PDF_OUTPUT_DIR = Path.of(System.getProperty("user.home"), "impromptu-pdfs");

    private final Map<String, StoredPdfInfo> storage = new ConcurrentHashMap<>();

    public LocalFilesystemPdfStorage() {
        try {
            Files.createDirectories(PDF_OUTPUT_DIR);
            logger.info("PDF output directory: {}", PDF_OUTPUT_DIR.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create PDF output directory", e);
        }
    }

    @Override
    public String store(PdfResult result) {
        var id = UUID.randomUUID().toString();
        var filePath = PDF_OUTPUT_DIR.resolve(result.filename());

        try {
            Files.write(filePath, result.pdfBytes());
            storage.put(id, new StoredPdfInfo(result.filename(), filePath));
            logger.info("Stored PDF: {} -> {}", id, filePath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write PDF to file", e);
        }

        return id;
    }

    @Override
    public Optional<String> getLocation(String storageId) {
        var info = storage.get(storageId);
        if (info == null) {
            return Optional.empty();
        }
        return Optional.of(info.filePath.toAbsolutePath().toString());
    }

    @Override
    public Optional<PdfResult> retrieve(String storageId) {
        var info = storage.get(storageId);
        if (info == null) {
            return Optional.empty();
        }

        try {
            var bytes = Files.readAllBytes(info.filePath);
            return Optional.of(new PdfResult(bytes, info.filename));
        } catch (IOException e) {
            logger.error("Failed to read PDF from file: {}", info.filePath, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String storageId) {
        var info = storage.remove(storageId);
        if (info == null) {
            return false;
        }

        try {
            Files.deleteIfExists(info.filePath);
            logger.info("Deleted PDF: {}", storageId);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete PDF file: {}", info.filePath, e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Local filesystem: " + PDF_OUTPUT_DIR.toAbsolutePath();
    }

    private record StoredPdfInfo(String filename, Path filePath) {}
}
