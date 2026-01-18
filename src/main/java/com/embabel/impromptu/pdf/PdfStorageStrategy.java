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

import java.util.Optional;

/**
 * Strategy interface for PDF storage.
 * Implementations can store PDFs in different backends (filesystem, S3, GCS, etc.).
 */
public interface PdfStorageStrategy {

    /**
     * Store a PDF and return a storage identifier.
     *
     * @param result the PDF to store
     * @return a storage identifier that can be used to retrieve the PDF
     */
    String store(PdfResult result);

    /**
     * Get the user-accessible location for a stored PDF.
     * This could be a file path, URL, or other location string.
     *
     * @param storageId the storage identifier returned by {@link #store}
     * @return the location, or empty if not found
     */
    Optional<String> getLocation(String storageId);

    /**
     * Retrieve the PDF bytes.
     *
     * @param storageId the storage identifier
     * @return the PDF result, or empty if not found
     */
    Optional<PdfResult> retrieve(String storageId);

    /**
     * Delete a stored PDF.
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
