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
package com.embabel.impromptu.data.pipeline;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Base interface for all data enhancers.
 * Each enhancer has two phases:
 * <ol>
 *   <li><b>Generate</b>: Process entities and output CSV for human review</li>
 *   <li><b>Apply</b>: Load reviewed CSV into database</li>
 * </ol>
 */
public interface Enhancer {

    /**
     * Unique identifier for the enhancer.
     */
    String getId();

    /**
     * Display name for the enhancer.
     */
    String getName();

    /**
     * Description of what this enhancer does.
     */
    String getDescription();

    /**
     * Output CSV path for this enhancer.
     */
    Path getOutputPath();

    /**
     * Check if generation should run.
     * @return true to generate, false to skip (e.g., CSV already exists)
     */
    default boolean shouldGenerate() {
        return true;
    }

    /**
     * Check if apply should run.
     * @return true to apply, false to skip (e.g., data already in database)
     */
    default boolean shouldApply() {
        return true;
    }

    /**
     * Generate CSV output for review.
     */
    void generate() throws IOException;

    /**
     * Apply the CSV data to the database (approved entries only).
     * @return result of the apply operation
     */
    default ApplyResult apply() throws IOException {
        return apply(false);
    }

    /**
     * Apply the CSV data to the database.
     * @param ignoreStatus if true, apply all entries regardless of status
     * @return result of the apply operation
     */
    ApplyResult apply(boolean ignoreStatus) throws IOException;

    /**
     * Result of applying CSV to database.
     */
    record ApplyResult(int loaded, int skipped, int failed) {
    }
}
