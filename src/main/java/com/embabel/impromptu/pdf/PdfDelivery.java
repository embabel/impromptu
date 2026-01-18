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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for temporarily storing generated PDFs for download.
 * PDFs are stored in memory with automatic expiration.
 */
@Service
public class PdfDelivery {

    private static final Logger logger = LoggerFactory.getLogger(PdfDelivery.class);
    private static final int EXPIRATION_MINUTES = 30;

    private final Map<String, StoredPdf> storage = new ConcurrentHashMap<>();

    /**
     * Store a PDF result and return a download ID.
     */
    public String store(PdfResult result) {
        var id = UUID.randomUUID().toString();
        var stored = new StoredPdf(result, Instant.now());
        storage.put(id, stored);
        logger.info("Stored PDF for download: {} ({})", id, result.filename());
        return id;
    }

    /**
     * Retrieve a stored PDF by its download ID.
     */
    public Optional<PdfResult> retrieve(String id) {
        var stored = storage.get(id);
        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(stored.result());
    }

    /**
     * Retrieve and remove a stored PDF (one-time download).
     */
    public Optional<PdfResult> retrieveAndRemove(String id) {
        var stored = storage.remove(id);
        if (stored == null) {
            return Optional.empty();
        }
        logger.info("PDF downloaded and removed: {}", id);
        return Optional.of(stored.result());
    }

    /**
     * Check if a PDF exists for the given ID.
     */
    public boolean exists(String id) {
        return storage.containsKey(id);
    }

    /**
     * Clean up expired PDFs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpired() {
        var cutoff = Instant.now().minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        var removed = storage.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
        if (removed) {
            logger.info("Cleaned up expired PDFs");
        }
    }

    private record StoredPdf(PdfResult result, Instant createdAt) {
    }
}
