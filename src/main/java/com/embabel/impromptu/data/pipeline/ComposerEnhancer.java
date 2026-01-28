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

import com.embabel.impromptu.domain.Composer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for composer enhancement plugins.
 * Each enhancer has two phases:
 * <ol>
 *   <li><b>Generate</b>: Process composers and output to CSV for human review</li>
 *   <li><b>Apply</b>: Read reviewed CSV and load approved entries to database</li>
 * </ol>
 */
public interface ComposerEnhancer {

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

    // ========== GENERATE PHASE ==========

    /**
     * Check if generation should run.
     * @return true to generate, false to skip (e.g., CSV already exists)
     */
    default boolean shouldGenerate() {
        return true;
    }

    /**
     * Called before generation begins.
     */
    default void beforeGenerate(List<Composer> composers) throws IOException {
    }

    /**
     * Process a single composer during generation.
     */
    void processComposer(Composer composer, List<Composer> allComposers);

    /**
     * Called periodically to commit/save progress during generation.
     */
    default void commitProgress(int processedCount) throws IOException {
    }

    /**
     * Called after all composers have been processed.
     */
    default void afterGenerate(List<Composer> composers) throws IOException {
    }

    // ========== APPLY PHASE ==========

    /**
     * Check if apply should run.
     * @return true to apply, false to skip (e.g., data already in database)
     */
    default boolean shouldApply() {
        return true;
    }

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
