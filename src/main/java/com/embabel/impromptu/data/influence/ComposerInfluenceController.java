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
package com.embabel.impromptu.data.influence;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for composer influence operations.
 */
@RestController
@RequestMapping("/api/influences")
public class ComposerInfluenceController {

    private final ComposerInfluenceGenerator generator;
    private final ComposerInfluenceLoader loader;

    public ComposerInfluenceController(ComposerInfluenceGenerator generator, ComposerInfluenceLoader loader) {
        this.generator = generator;
        this.loader = loader;
    }

    /**
     * Generate influence relationships for all composers.
     * Writes to data/influences/composer-influences.csv for review.
     */
    @PostMapping("/generate")
    public Map<String, Object> generate() throws IOException {
        generator.generateAllInfluences();
        return Map.of(
                "status", "complete",
                "file", ComposerInfluenceGenerator.INFLUENCES_CSV_PATH.toString(),
                "message", "Review CSV and change status to 'approved', then POST /api/influences/load"
        );
    }

    /**
     * Load approved influence relationships into Neo4j.
     */
    @PostMapping("/load")
    public ComposerInfluenceLoader.LoadResult load() throws IOException {
        return loader.loadApproved();
    }

    /**
     * Get statistics about influence relationships in the database.
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return loader.getStats();
    }

    /**
     * List all composers in the database.
     */
    @GetMapping("/composers")
    public List<ComposerInfluenceGenerator.ComposerInfo> composers() {
        return generator.loadComposers();
    }
}
