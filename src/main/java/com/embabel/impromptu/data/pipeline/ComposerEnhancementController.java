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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the composer enhancement pipeline.
 */
@RestController
@RequestMapping("/api/enhancers")
public class ComposerEnhancementController {

    private final ComposerEnhancementPipeline pipeline;

    public ComposerEnhancementController(ComposerEnhancementPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * List all available enhancers.
     */
    @GetMapping
    public List<ComposerEnhancementPipeline.EnhancerInfo> listEnhancers() {
        return pipeline.getEnhancerInfos();
    }

    /**
     * Get info about a specific enhancer.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ComposerEnhancementPipeline.EnhancerInfo> getEnhancer(@PathVariable String id) {
        return pipeline.getEnhancer(id)
                .map(ComposerEnhancementPipeline.EnhancerInfo::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== GENERATE ==========

    /**
     * Generate CSV for a specific enhancer.
     */
    @PostMapping("/{id}/generate")
    public Map<String, Object> generate(@PathVariable String id) throws IOException {
        var enhancer = pipeline.getEnhancer(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown enhancer: " + id));

        pipeline.generate(id);

        return Map.of(
                "status", "complete",
                "enhancer", enhancer.getId(),
                "outputPath", enhancer.getOutputPath().toString()
        );
    }

    /**
     * Generate CSVs for all enhancers.
     */
    @PostMapping("/generate-all")
    public Map<String, Object> generateAll() throws IOException {
        pipeline.generateAll();
        return Map.of(
                "status", "complete",
                "enhancers", pipeline.getEnhancers().stream().map(ComposerEnhancer::getId).toList()
        );
    }

    // ========== APPLY ==========

    /**
     * Apply CSV for a specific enhancer to the database.
     */
    @PostMapping("/{id}/apply")
    public ComposerEnhancer.ApplyResult apply(@PathVariable String id) throws IOException {
        pipeline.getEnhancer(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown enhancer: " + id));

        return pipeline.apply(id);
    }

    /**
     * Apply all CSVs to the database.
     */
    @PostMapping("/apply-all")
    public List<ComposerEnhancementPipeline.ApplyAllResult> applyAll() throws IOException {
        return pipeline.applyAll();
    }
}
