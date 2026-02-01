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
 * Resource storage strategy that writes files to the local filesystem.
 * Default implementation; activated when resource.storage=filesystem or not set.
 */
@Component
@ConditionalOnProperty(name = "resource.storage", havingValue = "filesystem", matchIfMissing = true)
public class LocalFilesystemResourceStorage implements ResourceStorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalFilesystemResourceStorage.class);
    private static final Path RESOURCE_OUTPUT_DIR = Path.of(System.getProperty("user.home"), "impromptu-resources");

    private final Map<String, StoredResourceInfo> storage = new ConcurrentHashMap<>();

    public LocalFilesystemResourceStorage() {
        try {
            Files.createDirectories(RESOURCE_OUTPUT_DIR);
            logger.info("Resource output directory: {}", RESOURCE_OUTPUT_DIR.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create resource output directory", e);
        }
    }

    @Override
    public String store(ResourceResult result) {
        var id = UUID.randomUUID().toString();
        var filePath = RESOURCE_OUTPUT_DIR.resolve(result.filename());

        try {
            Files.write(filePath, result.bytes());
            storage.put(id, new StoredResourceInfo(result.filename(), filePath));
            logger.info("Stored resource: {} -> {}", id, filePath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write resource to file", e);
        }

        return id;
    }

    @Override
    public Optional<String> getLocation(String storageId) {
        var info = storage.get(storageId);
        if (info == null) {
            return Optional.empty();
        }
        // Return web-accessible URL that ResourceDownloadController will serve
        return Optional.of("/api/resource/download/" + storageId);
    }

    @Override
    public Optional<ResourceResult> retrieve(String storageId) {
        var info = storage.get(storageId);
        if (info == null) {
            return Optional.empty();
        }

        try {
            var bytes = Files.readAllBytes(info.filePath);
            return Optional.of(new ResourceResult(bytes, info.filename));
        } catch (IOException e) {
            logger.error("Failed to read resource from file: {}", info.filePath, e);
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
            logger.info("Deleted resource: {}", storageId);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete resource file: {}", info.filePath, e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Local filesystem: " + RESOURCE_OUTPUT_DIR.toAbsolutePath();
    }

    private record StoredResourceInfo(String filename, Path filePath) {}
}
