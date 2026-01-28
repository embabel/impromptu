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

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.util.VisualizableTask;
import com.embabel.impromptu.domain.Composer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single pipeline that runs composer enhancers.
 * Each enhancer has two phases:
 * <ul>
 *   <li><b>Generate</b>: Process composers and output CSV for human review</li>
 *   <li><b>Apply</b>: Load reviewed CSV into database</li>
 * </ul>
 */
@Service
public class ComposerEnhancementPipeline {

    private static final Logger logger = LoggerFactory.getLogger(ComposerEnhancementPipeline.class);

    private final NamedEntityDataRepository entityRepository;
    private final Map<String, ComposerEnhancer> composerEnhancersById;
    private final List<ComposerEnhancer> composerEnhancers;
    private final Map<String, Enhancer> allEnhancersById;
    private final List<Enhancer> allEnhancers;

    /** How often to log progress (every N composers). */
    private int progressInterval = 5;

    /** How often to commit/save progress (every N composers). */
    private int commitInterval = 10;

    public ComposerEnhancementPipeline(
            NamedEntityDataRepository entityRepository,
            List<ComposerEnhancer> composerEnhancers,
            List<Enhancer> allEnhancers) {
        this.entityRepository = entityRepository;
        this.composerEnhancers = composerEnhancers;
        this.composerEnhancersById = composerEnhancers.stream()
                .collect(Collectors.toMap(ComposerEnhancer::getId, Function.identity()));

        // Combine all enhancers (composer enhancers + standalone enhancers)
        // Filter out composer enhancers from allEnhancers to avoid duplicates
        var standaloneEnhancers = allEnhancers.stream()
                .filter(e -> !(e instanceof ComposerEnhancer))
                .toList();

        this.allEnhancers = new java.util.ArrayList<>();
        this.allEnhancers.addAll(composerEnhancers);
        this.allEnhancers.addAll(standaloneEnhancers);

        this.allEnhancersById = this.allEnhancers.stream()
                .collect(Collectors.toMap(Enhancer::getId, Function.identity()));

        logger.info("Discovered {} composer enhancers: {}",
                composerEnhancers.size(),
                composerEnhancers.stream().map(ComposerEnhancer::getId).toList());
        logger.info("Discovered {} standalone enhancers: {}",
                standaloneEnhancers.size(),
                standaloneEnhancers.stream().map(Enhancer::getId).toList());
    }

    /**
     * Get all enhancers (both composer and standalone).
     */
    public List<Enhancer> getAllEnhancers() {
        return allEnhancers;
    }

    /**
     * Get composer enhancers only.
     */
    public List<ComposerEnhancer> getEnhancers() {
        return composerEnhancers;
    }

    public Optional<Enhancer> getEnhancer(String id) {
        return Optional.ofNullable(allEnhancersById.get(id));
    }

    public Optional<ComposerEnhancer> getComposerEnhancer(String id) {
        return Optional.ofNullable(composerEnhancersById.get(id));
    }

    private List<Composer> loadComposers() {
        return entityRepository.findAll(Composer.class).stream()
                .sorted(Comparator.comparing(c -> c.getBirthYear() != null ? c.getBirthYear() : 0L))
                .toList();
    }

    // ========== GENERATE ==========

    /**
     * Generate CSV for a specific enhancer.
     */
    public void generate(String enhancerId) throws IOException {
        generate(enhancerId, null);
    }

    /**
     * Generate CSV for a specific enhancer with progress callback.
     */
    public void generate(String enhancerId, Consumer<Progress> progressCallback) throws IOException {
        var enhancer = allEnhancersById.get(enhancerId);
        if (enhancer == null) {
            throw new IllegalArgumentException("Unknown enhancer: " + enhancerId);
        }

        if (enhancer instanceof ComposerEnhancer ce) {
            generateForComposerEnhancer(ce, progressCallback);
        } else {
            generateForEnhancer(enhancer);
        }
    }

    /**
     * Generate CSVs for all enhancers.
     */
    public void generateAll() throws IOException {
        generateAll(null);
    }

    /**
     * Generate CSVs for all enhancers with progress callback.
     */
    public void generateAll(Consumer<Progress> progressCallback) throws IOException {
        for (var enhancer : allEnhancers) {
            if (enhancer instanceof ComposerEnhancer ce) {
                generateForComposerEnhancer(ce, progressCallback);
            } else {
                generateForEnhancer(enhancer);
            }
        }
    }

    private void generateForEnhancer(Enhancer enhancer) throws IOException {
        logger.info("Generating: {} ({})", enhancer.getName(), enhancer.getId());

        if (!enhancer.shouldGenerate()) {
            logger.info("[{}] Skipping generation - CSV already exists", enhancer.getId());
            return;
        }

        enhancer.generate();
        logger.info("[{}] Generation complete", enhancer.getId());
    }

    private void generateForComposerEnhancer(ComposerEnhancer enhancer, Consumer<Progress> progressCallback) throws IOException {
        logger.info("Generating: {} ({})", enhancer.getName(), enhancer.getId());

        if (!enhancer.shouldGenerate()) {
            logger.info("[{}] Skipping generation - CSV already exists", enhancer.getId());
            return;
        }

        var composers = loadComposers();
        logger.info("[{}] Loaded {} composers from database", enhancer.getId(), composers.size());

        enhancer.beforeGenerate(composers);

        int total = composers.size();
        int processed = 0;

        reportProgress(progressCallback, enhancer, processed, total, "Starting...");

        for (var composer : composers) {
            enhancer.processComposer(composer, composers);
            processed++;

            if (processed % commitInterval == 0) {
                enhancer.commitProgress(processed);
            }

            if (processed % progressInterval == 0 || processed == total) {
                reportProgress(progressCallback, enhancer, processed, total, composer.getName());
            }
        }

        enhancer.commitProgress(processed);
        enhancer.afterGenerate(composers);

        logger.info("[{}] Generation complete: {} processed", enhancer.getId(), processed);
    }

    // ========== APPLY ==========

    /**
     * Apply CSV for a specific enhancer (approved entries only).
     */
    public Enhancer.ApplyResult apply(String enhancerId) throws IOException {
        return apply(enhancerId, false);
    }

    /**
     * Apply CSV for a specific enhancer.
     * @param ignoreStatus if true, apply all entries regardless of status
     */
    public Enhancer.ApplyResult apply(String enhancerId, boolean ignoreStatus) throws IOException {
        var enhancer = allEnhancersById.get(enhancerId);
        if (enhancer == null) {
            throw new IllegalArgumentException("Unknown enhancer: " + enhancerId);
        }
        return applyForEnhancer(enhancer, ignoreStatus);
    }

    /**
     * Apply CSVs for all enhancers (approved entries only).
     */
    public List<ApplyAllResult> applyAll() throws IOException {
        return applyAll(false);
    }

    /**
     * Apply CSVs for all enhancers.
     * @param ignoreStatus if true, apply all entries regardless of status
     */
    public List<ApplyAllResult> applyAll(boolean ignoreStatus) throws IOException {
        return allEnhancers.stream()
                .map(e -> {
                    try {
                        return new ApplyAllResult(e.getId(), applyForEnhancer(e, ignoreStatus));
                    } catch (IOException ex) {
                        logger.error("Failed to apply {}: {}", e.getId(), ex.getMessage());
                        return new ApplyAllResult(e.getId(), new Enhancer.ApplyResult(0, 0, -1));
                    }
                })
                .toList();
    }

    private Enhancer.ApplyResult applyForEnhancer(Enhancer enhancer, boolean ignoreStatus) throws IOException {
        logger.info("Applying: {} ({}){}",
                enhancer.getName(), enhancer.getId(), ignoreStatus ? " [ignoring status]" : "");

        if (!ignoreStatus && !enhancer.shouldApply()) {
            logger.info("[{}] Skipping apply - data already in database", enhancer.getId());
            return new Enhancer.ApplyResult(0, 0, 0);
        }

        var result = enhancer.apply(ignoreStatus);
        logger.info("[{}] Apply complete: {} loaded, {} skipped, {} failed",
                enhancer.getId(), result.loaded(), result.skipped(), result.failed());
        return result;
    }

    // ========== HELPERS ==========

    private void reportProgress(Consumer<Progress> callback, ComposerEnhancer enhancer,
                                int current, int total, String currentItem) {
        var progress = new Progress(enhancer.getId(), enhancer.getName(), current, total, currentItem);

        if (total > 0) {
            var task = VisualizableTask.Companion.invoke(enhancer.getName(), current, total);
            logger.info(task.createProgressBar(50));
        } else {
            logger.info("[{}] No items to process", enhancer.getId());
        }

        if (callback != null) {
            callback.accept(progress);
        }
    }

    public void setProgressInterval(int interval) {
        this.progressInterval = interval;
    }

    public void setCommitInterval(int interval) {
        this.commitInterval = interval;
    }

    public record Progress(
            String enhancerId,
            String enhancerName,
            int current,
            int total,
            String currentItem
    ) {
        public double percentage() {
            return total > 0 ? (double) current / total * 100 : 0;
        }

        public boolean isComplete() {
            return current >= total;
        }
    }

    public record EnhancerInfo(
            String id,
            String name,
            String description,
            String outputPath
    ) {
        public static EnhancerInfo from(Enhancer enhancer) {
            return new EnhancerInfo(
                    enhancer.getId(),
                    enhancer.getName(),
                    enhancer.getDescription(),
                    enhancer.getOutputPath() != null ? enhancer.getOutputPath().toString() : null
            );
        }
    }

    public record ApplyAllResult(String enhancerId, Enhancer.ApplyResult result) {
    }

    public List<EnhancerInfo> getEnhancerInfos() {
        return allEnhancers.stream()
                .map(EnhancerInfo::from)
                .toList();
    }
}
