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
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.domain.Composer;
import com.embabel.impromptu.domain.Nationality;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates composer-nationality relationships using LLM general knowledge.
 * Identifies the nationality/nationalities of each composer.
 * <p>
 * Generate phase: Processes all composers, outputs CSV for human review.
 * Apply phase: Loads approved entries from CSV into database as HAS_NATIONALITY relationships.
 */
@Service
public class ComposerNationalityEnhancer implements ComposerEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(ComposerNationalityEnhancer.class);

    public static final Path NATIONALITIES_CSV_PATH = Path.of("data/nationalities/composer-nationalities.csv");

    public record NationalityRecord(
            String composerId,
            String nationalityId,
            String reason,
            double confidence,
            String status
    ) {
        public String toCsvLine() {
            var escapedReason = reason.replace("\"", "\"\"");
            return String.format("%s,%s,\"%s\",%.2f,%s",
                    composerId, nationalityId, escapedReason, confidence, status);
        }
    }

    private final Ai ai;
    private final ImpromptuProperties properties;
    private final NamedEntityDataRepository entityRepository;
    private final PersistenceManager persistenceManager;

    private List<String> pendingRecords;
    private PrintWriter csvWriter;
    private List<Nationality> allNationalities;

    record NationalityResponse(List<NationalityAssignment> nationalities) {
    }

    record NationalityAssignment(
            String nationalityId,
            String reason,
            double confidence
    ) {
    }

    public ComposerNationalityEnhancer(
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
        return "composer-nationalities";
    }

    @Override
    public String getName() {
        return "Composer Nationalities";
    }

    @Override
    public String getDescription() {
        return "Generates HAS_NATIONALITY relationships between composers and nationalities using LLM general knowledge";
    }

    @Override
    public Path getOutputPath() {
        return NATIONALITIES_CSV_PATH;
    }

    @Override
    public boolean shouldGenerate() {
        if (Files.exists(NATIONALITIES_CSV_PATH)) {
            try {
                var lines = Files.readAllLines(NATIONALITIES_CSV_PATH);
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
        Files.createDirectories(NATIONALITIES_CSV_PATH.getParent());
        this.pendingRecords = new ArrayList<>();
        this.allNationalities = entityRepository.findAll(Nationality.class);

        logger.info("Loaded {} nationalities from database", allNationalities.size());

        this.csvWriter = new PrintWriter(new FileWriter(NATIONALITIES_CSV_PATH.toFile(), false));
        csvWriter.println("composer_id,nationality_id,reason,confidence,status");
        csvWriter.flush();
    }

    @Override
    public void processComposer(Composer composer, List<Composer> allComposers) {
        var assignments = generateNationalitiesFor(composer);
        for (var assignment : assignments) {
            pendingRecords.add(assignment.toCsvLine());
            logger.info("  {} -> {} (confidence={})",
                    composer.getId(), assignment.nationalityId(), assignment.confidence());
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
        logger.info("Nationality generation complete. Output: {}", NATIONALITIES_CSV_PATH);

        this.pendingRecords = null;
        this.csvWriter = null;
        this.allNationalities = null;
    }

    // ========== APPLY PHASE ==========

    @Override
    public boolean shouldApply() {
        var count = countHasNationalityRelationships();
        if (count > 0) {
            logger.info("Database already has {} HAS_NATIONALITY relationships - skipping apply", count);
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public ApplyResult apply(boolean ignoreStatus) throws IOException {
        if (!Files.exists(NATIONALITIES_CSV_PATH)) {
            logger.warn("CSV file not found: {}", NATIONALITIES_CSV_PATH);
            return new ApplyResult(0, 0, 1);
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
                createHasNationalityRelationship(record);
                loaded++;
            } catch (Exception e) {
                logger.warn("Failed to create HAS_NATIONALITY for {} -> {}: {}",
                        record.composerId(), record.nationalityId(), e.getMessage());
                failed++;
            }
        }

        logger.info("Applied {} HAS_NATIONALITY relationships ({} skipped, {} failed)", loaded, skipped, failed);
        return new ApplyResult(loaded, skipped, failed);
    }

    // ========== HELPERS ==========

    private List<NationalityRecord> generateNationalitiesFor(Composer composer) {
        var nationalityList = allNationalities.stream()
                .map(n -> n.getId() + " (" + n.getName() + ")")
                .toList();

        try {
            var response = ai
                    .withLlm(properties.propositionExtractionLlm())
                    .withId("generate_composer_nationalities")
                    .rendering("generate_composer_nationalities")
                    .createObject(NationalityResponse.class, Map.of(
                            "composerId", composer.getId(),
                            "composerName", composer.getCompleteName(),
                            "composerShortName", composer.getName(),
                            "birthYear", String.valueOf(composer.getBirthYear()),
                            "deathYear", composer.getDeathYear() != null ? String.valueOf(composer.getDeathYear()) : "present",
                            "nationalityList", String.join("\n", nationalityList)
                    ));

            if (response == null || response.nationalities() == null) {
                return List.of();
            }

            return response.nationalities().stream()
                    .map(n -> new NationalityRecord(
                            composer.getId(),
                            n.nationalityId(),
                            n.reason(),
                            n.confidence(),
                            "pending"
                    ))
                    .toList();
        } catch (Exception e) {
            logger.warn("Failed to generate nationalities for {}: {}", composer.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<NationalityRecord> readCsv() throws IOException {
        var records = new ArrayList<NationalityRecord>();

        try (var reader = new BufferedReader(new FileReader(NATIONALITIES_CSV_PATH.toFile()))) {
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

    private NationalityRecord parseCsvLine(String line) {
        // CSV format: composer_id,nationality_id,"reason",confidence,status
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

        if (parts.size() < 5) {
            return null;
        }

        return new NationalityRecord(
                parts.get(0),
                parts.get(1),
                parts.get(2),
                Double.parseDouble(parts.get(3)),
                parts.get(4)
        );
    }

    private void createHasNationalityRelationship(NationalityRecord record) {
        var query = """
                MATCH (c:Composer {id: $composerId}), (n:Nationality {id: $nationalityId})
                MERGE (c)-[r:HAS_NATIONALITY]->(n)
                SET r.reason = $reason, r.confidence = $confidence
                """;

        persistenceManager.execute(
                QuerySpecification.withStatement(query)
                        .bind(Map.of(
                                "composerId", record.composerId(),
                                "nationalityId", record.nationalityId(),
                                "reason", record.reason(),
                                "confidence", record.confidence()
                        ))
        );
    }

    public long countHasNationalityRelationships() {
        var query = "MATCH ()-[r:HAS_NATIONALITY]->() RETURN count(r) AS count";
        return persistenceManager.getOne(
                QuerySpecification.withStatement(query).transform(Long.class)
        );
    }

    @Transactional
    public long deleteAllHasNationalityRelationships() {
        var count = countHasNationalityRelationships();
        var query = "MATCH ()-[r:HAS_NATIONALITY]->() DELETE r";
        persistenceManager.execute(QuerySpecification.withStatement(query));
        logger.info("Deleted {} HAS_NATIONALITY relationships", count);
        return count;
    }
}
