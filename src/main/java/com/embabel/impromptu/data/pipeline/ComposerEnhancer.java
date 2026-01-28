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
 * <p>
 * Composer enhancers are driven by the {@link ComposerEnhancementPipeline} which
 * loads composers and calls the lifecycle methods. The {@link #generate()} method
 * from {@link Enhancer} is not used directly - use the pipeline instead.
 */
public interface ComposerEnhancer extends Enhancer {

    // ========== GENERATE PHASE ==========

    /**
     * Not used for ComposerEnhancer - use the pipeline instead.
     * This is implemented to satisfy the Enhancer interface.
     */
    @Override
    default void generate() throws IOException {
        throw new UnsupportedOperationException(
                "ComposerEnhancer.generate() should not be called directly. Use ComposerEnhancementPipeline instead.");
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

}
