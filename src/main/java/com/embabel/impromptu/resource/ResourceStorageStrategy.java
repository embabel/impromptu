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

import java.util.Optional;

/**
 * Strategy interface for resource storage.
 * Implementations can store resources in different backends (filesystem, S3, GCS, etc.).
 */
public interface ResourceStorageStrategy {

    /**
     * Store a resource and return a storage identifier.
     *
     * @param result the resource to store
     * @return a storage identifier that can be used to retrieve the resource
     */
    String store(ResourceResult result);

    /**
     * Get the user-accessible location for a stored resource.
     * This could be a file path, URL, or other location string.
     *
     * @param storageId the storage identifier returned by {@link #store}
     * @return the location, or empty if not found
     */
    Optional<String> getLocation(String storageId);

    /**
     * Retrieve the resource bytes.
     *
     * @param storageId the storage identifier
     * @return the resource result, or empty if not found
     */
    Optional<ResourceResult> retrieve(String storageId);

    /**
     * Delete a stored resource.
     *
     * @param storageId the storage identifier
     * @return true if deleted, false if not found
     */
    boolean delete(String storageId);

    /**
     * Get a description of this storage strategy for display.
     */
    String getDescription();
}
