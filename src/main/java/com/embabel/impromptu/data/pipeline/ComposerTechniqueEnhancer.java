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

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.domain.reference.Composer;
import com.embabel.impromptu.domain.reference.Technique;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates composer-technique relationships using LLM general knowledge.
 * Identifies which compositional techniques each composer used.
 * <p>
 * Generate phase: Processes all composers, outputs CSV for human review.
 * Apply phase: Loads approved entries from CSV into database as USES relationships.
 */
@Service
public class ComposerTechniqueEnhancer implements ComposerEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(ComposerTechniqueEnhancer.class);

    public static final Path TECHNIQUES_CSV_PATH = Path.of("data/techniques/composer-techniques.csv");

    public record TechniqueUsageRecord(
            String composerId,
            String techniqueId,
            String reason,
            double strength,
            double confidence,
            String status
    ) {
        public String toCsvLine() {
            var escapedReason = reason.replace("\"", "\"\"");
            return String.format("%s,%s,\"%s\",%.2f,%.2f,%s",
                    composerId, techniqueId, escapedReason, strength, confidence, status);
        }
    }

    private final Ai ai;
    private final ImpromptuProperties properties;
    private final NamedEntityDataRepository entityRepository;
    private final PersistenceManager persistenceManager;

    private List<String> pendingRecords;
    private PrintWriter csvWriter;
    private List<Technique> allTechniques;

    record TechniqueResponse(List<TechniqueUsage> techniques) {
    }

    record TechniqueUsage(
            String techniqueId,
            String reason,
            double strength,
            double confidence
    ) {
    }

    public ComposerTechniqueEnhancer(
            AiBuilder aiBuilder,
            ImpromptuProperties properties,
            NamedEntityDataRepository entityRepository,
            PersistenceManager persistenceManager) {
        this.ai = aiBuilder.ai();
        this.properties = properties;
        this.entityRepository = entityRepository;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String getId() {
        return "composer-techniques";
    }

    @Override
    public String getName() {
        return "Composer Techniques";
    }

    @Override
    public String getDescription() {
        return "Generates USES relationships between composers and techniques using LLM general knowledge";
    }

    @Override
    public Path getOutputPath() {
        return TECHNIQUES_CSV_PATH;
    }

    // ========== GENERATE PHASE ==========

    @Override
    public boolean shouldGenerate() {
        if (Files.exists(TECHNIQUES_CSV_PATH)) {
            try {
                var lines = Files.readAllLines(TECHNIQUES_CSV_PATH);
                if (lines.size() > 1) {
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
        Files.createDirectories(TECHNIQUES_CSV_PATH.getParent());
        this.pendingRecords = new ArrayList<>();
        this.allTechniques = entityRepository.findAll(Technique.class);

        logger.info("Loaded {} techniques from database", allTechniques.size());

        this.csvWriter = new PrintWriter(new FileWriter(TECHNIQUES_CSV_PATH.toFile(), false));
        csvWriter.println("composer_id,technique_id,reason,strength,confidence,status");
        csvWriter.flush();
    }

    @Override
    public void processComposer(Composer composer, List<Composer> allComposers) {
        var usages = generateTechniqueUsagesFor(composer);
        for (var usage : usages) {
            pendingRecords.add(usage.toCsvLine());
            logger.info("  {} uses {} (strength={}, confidence={})",
                    composer.getId(), usage.techniqueId(), usage.strength(), usage.confidence());
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
        logger.info("Technique generation complete. Output: {}", TECHNIQUES_CSV_PATH);

        this.pendingRecords = null;
        this.csvWriter = null;
        this.allTechniques = null;
    }

    // ========== APPLY PHASE ==========

    @Override
    public boolean shouldApply() {
        var count = countUsesRelationships();
        if (count > 0) {
            logger.info("Database already has {} USES relationships - skipping apply", count);
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public Enhancer.ApplyResult apply(boolean ignoreStatus) throws IOException {
        if (!Files.exists(TECHNIQUES_CSV_PATH)) {
            logger.warn("CSV file not found: {}", TECHNIQUES_CSV_PATH);
            return new Enhancer.ApplyResult(0, 0, 1);
        }

        var records = readCsv();
        int loaded = 0;
        int skipped = 0;
        int failed = 0;

        for (var record : records) {
            if (!ignoreStatus && !"approved".equalsIgnoreCase(record.status())) {
                skipped++;
                continue;
            }

            try {
                createUsesRelationship(record);
                loaded++;
            } catch (Exception e) {
                logger.warn("Failed to create USES for {} -> {}: {}",
                        record.composerId(), record.techniqueId(), e.getMessage());
                failed++;
            }
        }

        logger.info("Applied {} USES relationships ({} skipped, {} failed)", loaded, skipped, failed);
        return new Enhancer.ApplyResult(loaded, skipped, failed);
    }

    // ========== HELPERS ==========

    private List<TechniqueUsageRecord> generateTechniqueUsagesFor(Composer composer) {
        var techniqueList = allTechniques.stream()
                .map(t -> t.getId() + " (" + t.getName() + ")")
                .toList();

        try {
            var response = ai
                    .withLlm(properties.propositionExtractionLlm())
                    .withId("generate_composer_techniques")
                    .rendering("enhancers/generate_composer_techniques")
                    .createObject(TechniqueResponse.class, Map.of(
                            "composerId", composer.getId(),
                            "composerName", composer.getCompleteName(),
                            "composerShortName", composer.getName(),
                            "birthYear", String.valueOf(composer.getBirthYear()),
                            "deathYear", composer.getDeathYear() != null ? String.valueOf(composer.getDeathYear()) : "present",
                            "techniqueList", String.join("\n", techniqueList)
                    ));

            if (response == null || response.techniques() == null) {
                return List.of();
            }

            return response.techniques().stream()
                    .map(t -> new TechniqueUsageRecord(
                            composer.getId(),
                            t.techniqueId(),
                            t.reason(),
                            t.strength(),
                            t.confidence(),
                            "pending"
                    ))
                    .toList();
        } catch (Exception e) {
            logger.warn("Failed to generate techniques for {}: {}", composer.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<TechniqueUsageRecord> readCsv() throws IOException {
        var records = new ArrayList<TechniqueUsageRecord>();

        try (var reader = new BufferedReader(new FileReader(TECHNIQUES_CSV_PATH.toFile()))) {
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }

                try {
                    var record = parseCsvLine(line);
                    if (record != null) {
                        records.add(record);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse CSV line: {}", line);
                }
            }
        }

        return records;
    }

    private TechniqueUsageRecord parseCsvLine(String line) {
        // CSV format: composer_id,technique_id,"reason",strength,confidence,status
        var parts = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());

        if (parts.size() < 6) {
            return null;
        }

        return new TechniqueUsageRecord(
                parts.get(0),
                parts.get(1),
                parts.get(2),
                Double.parseDouble(parts.get(3)),
                Double.parseDouble(parts.get(4)),
                parts.get(5)
        );
    }

    private void createUsesRelationship(TechniqueUsageRecord record) {
        var query = """
                MATCH (c:Composer {id: $composerId}), (t:Technique {id: $techniqueId})
                MERGE (c)-[r:USES]->(t)
                SET r.reason = $reason, r.strength = $strength, r.confidence = $confidence
                """;

        persistenceManager.execute(
                QuerySpecification.withStatement(query)
                        .bind(Map.of(
                                "composerId", record.composerId(),
                                "techniqueId", record.techniqueId(),
                                "reason", record.reason(),
                                "strength", record.strength(),
                                "confidence", record.confidence()
                        ))
        );
    }

    public long countUsesRelationships() {
        var query = "MATCH ()-[r:USES]->() RETURN count(r) AS count";
        return persistenceManager.getOne(
                QuerySpecification.withStatement(query).transform(Long.class)
        );
    }

    @Transactional
    public long deleteAllUsesRelationships() {
        var count = countUsesRelationships();
        var query = "MATCH ()-[r:USES]->() DELETE r";
        persistenceManager.execute(QuerySpecification.withStatement(query));
        logger.info("Deleted {} USES relationships", count);
        return count;
    }
}
