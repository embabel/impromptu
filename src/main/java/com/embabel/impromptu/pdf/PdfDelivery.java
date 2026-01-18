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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for storing generated PDFs and managing their lifecycle.
 * Delegates actual storage to a {@link PdfStorageStrategy}.
 */
@Service
public class PdfDelivery {

    private static final Logger logger = LoggerFactory.getLogger(PdfDelivery.class);
    private static final int EXPIRATION_MINUTES = 30;

    private final PdfStorageStrategy storageStrategy;
    private final Map<String, Instant> expirationTracker = new ConcurrentHashMap<>();

    public PdfDelivery(PdfStorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
        logger.info("PDF delivery initialized with storage: {}", storageStrategy.getDescription());
    }

    /**
     * Store a PDF result and return a storage ID.
     */
    public String store(PdfResult result) {
        var id = storageStrategy.store(result);
        expirationTracker.put(id, Instant.now());
        logger.info("PDF stored: {} ({} bytes)", result.filename(), result.size());
        return id;
    }

    /**
     * Get the user-accessible location for a stored PDF.
     */
    public Optional<String> getLocation(String id) {
        if (!expirationTracker.containsKey(id)) {
            return Optional.empty();
        }
        return storageStrategy.getLocation(id);
    }

    /**
     * Retrieve a stored PDF by its storage ID.
     */
    public Optional<PdfResult> retrieve(String id) {
        if (!expirationTracker.containsKey(id)) {
            return Optional.empty();
        }
        return storageStrategy.retrieve(id);
    }

    /**
     * Retrieve and remove a stored PDF (one-time download).
     */
    public Optional<PdfResult> retrieveAndRemove(String id) {
        expirationTracker.remove(id);
        var result = storageStrategy.retrieve(id);
        if (result.isPresent()) {
            storageStrategy.delete(id);
            logger.info("PDF downloaded and removed: {}", id);
        }
        return result;
    }

    /**
     * Check if a PDF exists for the given ID.
     */
    public boolean exists(String id) {
        return expirationTracker.containsKey(id);
    }

    /**
     * Clean up expired PDFs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpired() {
        var cutoff = Instant.now().minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        var expiredIds = expirationTracker.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();

        for (var id : expiredIds) {
            expirationTracker.remove(id);
            storageStrategy.delete(id);
        }

        if (!expiredIds.isEmpty()) {
            logger.info("Cleaned up {} expired PDFs", expiredIds.size());
        }
    }
}
