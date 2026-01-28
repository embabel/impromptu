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

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.AiBuilder;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.data.influence.ComposerInfluenceLoader;
import com.embabel.impromptu.domain.Composer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates composer influence relationships using LLM general knowledge.
 * <p>
 * Generate phase: Processes all composers, outputs CSV for human review.
 * Apply phase: Loads approved entries from CSV into database.
 */
@Service
public class GeneralKnowledgeLlmInfluenceComposerEnhancer implements ComposerEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(GeneralKnowledgeLlmInfluenceComposerEnhancer.class);

    public static final Path INFLUENCES_CSV_PATH = Path.of("data/influences/composer-influences.csv");

    public record InfluenceRecord(
            String from,
            String to,
            String reason,
            double strength,
            double confidence,
            double divergence,
            String status
    ) {
        public String toCsvLine() {
            var escapedReason = reason.replace("\"", "\"\"");
            return String.format("%s,%s,\"%s\",%.2f,%.2f,%.2f,%s",
                    from, to, escapedReason, strength, confidence, divergence, status);
        }
    }

    private final Ai ai;
    private final ImpromptuProperties properties;
    private final ComposerInfluenceLoader influenceLoader;

    private List<String> pendingRecords;
    private PrintWriter csvWriter;

    record InfluenceResponse(List<GeneratedInfluence> influences) {}

    record GeneratedInfluence(
            String from,
            String to,
            String reason,
            double strength,
            double confidence,
            double divergence
    ) {}

    public GeneralKnowledgeLlmInfluenceComposerEnhancer(
            AiBuilder aiBuilder,
            ImpromptuProperties properties,
            ComposerInfluenceLoader influenceLoader) {
        this.ai = aiBuilder.ai();
        this.properties = properties;
        this.influenceLoader = influenceLoader;
    }

    @Override
    public String getId() {
        return "composer-influences";
    }

    @Override
    public String getName() {
        return "Composer Influences";
    }

    @Override
    public String getDescription() {
        return "Generates INFLUENCED relationships between composers using LLM general knowledge";
    }

    @Override
    public Path getOutputPath() {
        return INFLUENCES_CSV_PATH;
    }

    // ========== GENERATE PHASE ==========

    @Override
    public boolean shouldGenerate() {
        // Skip if CSV already exists with content
        if (Files.exists(INFLUENCES_CSV_PATH)) {
            try {
                var lines = Files.readAllLines(INFLUENCES_CSV_PATH);
                if (lines.size() > 1) { // More than just header
                    logger.info("CSV already exists with {} entries - skipping generation", lines.size() - 1);
                    return false;
                }
            } catch (IOException e) {
                logger.warn("Could not read CSV: {}", e.getMessage());
            }
        }
        return true;
    }

    @Override
    public void beforeGenerate(List<Composer> composers) throws IOException {
        Files.createDirectories(INFLUENCES_CSV_PATH.getParent());
        this.pendingRecords = new ArrayList<>();

        this.csvWriter = new PrintWriter(new FileWriter(INFLUENCES_CSV_PATH.toFile(), false));
        csvWriter.println("from,to,reason,strength,confidence,divergence,status");
        csvWriter.flush();
    }

    @Override
    public void processComposer(Composer composer, List<Composer> allComposers) {
        var influences = generateInfluencesFor(composer, allComposers);
        for (var inf : influences) {
            pendingRecords.add(inf.toCsvLine());
            logger.info("  {} -> {} (strength={}, confidence={}, divergence={})",
                    inf.from(), inf.to(), inf.strength(), inf.confidence(), inf.divergence());
        }
    }

    @Override
    public void commitProgress(int processedCount) throws IOException {
        if (pendingRecords.isEmpty()) {
            return;
        }

        for (var line : pendingRecords) {
            csvWriter.println(line);
        }
        csvWriter.flush();

        logger.info("Committed {} records to CSV (total processed: {})", pendingRecords.size(), processedCount);
        pendingRecords.clear();
    }

    @Override
    public void afterGenerate(List<Composer> composers) throws IOException {
        if (pendingRecords != null && !pendingRecords.isEmpty()) {
            for (var line : pendingRecords) {
                csvWriter.println(line);
            }
        }

        if (csvWriter != null) {
            csvWriter.close();
        }
        logger.info("Influence generation complete. Output: {}", INFLUENCES_CSV_PATH);

        this.pendingRecords = null;
        this.csvWriter = null;
    }

    // ========== APPLY PHASE ==========

    @Override
    public boolean shouldApply() {
        // Skip if there are already influence relationships in the database
        var count = influenceLoader.countInfluenceRelationships();
        if (count > 0) {
            logger.info("Database already has {} influence relationships - skipping apply", count);
            return false;
        }
        return true;
    }

    @Override
    public ApplyResult apply(boolean ignoreStatus) throws IOException {
        var result = ignoreStatus ? influenceLoader.loadAll() : influenceLoader.loadApproved();
        return new ApplyResult(result.loaded(), result.skipped(), result.notFound());
    }

    // ========== LLM GENERATION ==========

    private List<InfluenceRecord> generateInfluencesFor(Composer composer, List<Composer> allComposers) {
        long influenceCutoff = composer.getDeathYear() != null
                ? composer.getDeathYear()
                : (composer.getBirthYear() != null ? composer.getBirthYear() + 80 : Long.MAX_VALUE);

        var composerList = allComposers.stream()
                .filter(c -> !c.getId().equals(composer.getId()))
                .filter(c -> c.getBirthYear() != null && c.getBirthYear() < influenceCutoff)
                .map(c -> c.getId() + " (" + c.getCompleteName() + ", " + c.getBirthYear() + "-" +
                        (c.getDeathYear() != null ? c.getDeathYear() : "present") + ")")
                .toList();

        try {
            var response = ai
                    .withLlm(properties.propositionExtractionLlm())
                    .withId("generate_composer_influences")
                    .rendering("generate_composer_influences")
                    .createObject(InfluenceResponse.class, Map.of(
                            "composerId", composer.getId(),
                            "composerName", composer.getCompleteName(),
                            "composerShortName", composer.getName(),
                            "birthYear", String.valueOf(composer.getBirthYear()),
                            "deathYear", composer.getDeathYear() != null ? String.valueOf(composer.getDeathYear()) : "present",
                            "composerList", String.join("\n", composerList)
                    ));

            if (response == null || response.influences() == null) {
                return List.of();
            }

            return response.influences().stream()
                    .map(inf -> new InfluenceRecord(
                            inf.from(),
                            inf.to(),
                            inf.reason(),
                            inf.strength(),
                            inf.confidence(),
                            inf.divergence(),
                            "pending"
                    ))
                    .toList();
        } catch (Exception e) {
            logger.warn("Failed to generate influences for {}: {}", composer.getId(), e.getMessage());
            return List.of();
        }
    }
}
