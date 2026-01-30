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

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.domain.reference.Ensemble;
import com.embabel.impromptu.domain.reference.Instrument;
import com.embabel.impromptu.domain.reference.Work;
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
 * Infers instrumentation for works based on title pattern matching.
 * No LLM required - uses data-driven matching against Instrument and Ensemble names.
 * <p>
 * Creates SCORED_FOR and FEATURES relationships:
 * <ul>
 *   <li>SCORED_FOR - all instruments/ensembles required for the work</li>
 *   <li>FEATURES - featured solo instruments (in concertos)</li>
 * </ul>
 */
@Service
public class WorkInstrumentationEnhancer implements Enhancer {

    private static final Logger logger = LoggerFactory.getLogger(WorkInstrumentationEnhancer.class);

    public static final Path OUTPUT_PATH = Path.of("data/enhancements/work-instrumentation.csv");

    private final NamedEntityDataRepository entityRepository;
    private final PersistenceManager persistenceManager;

    public WorkInstrumentationEnhancer(
            NamedEntityDataRepository entityRepository,
            PersistenceManager persistenceManager) {
        this.entityRepository = entityRepository;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String getId() {
        return "work-instrumentation";
    }

    @Override
    public String getName() {
        return "Work Instrumentation";
    }

    @Override
    public String getDescription() {
        return "Infers SCORED_FOR and FEATURES relationships from work titles (no LLM)";
    }

    @Override
    public Path getOutputPath() {
        return OUTPUT_PATH;
    }

    @Override
    public boolean shouldGenerate() {
        if (Files.exists(OUTPUT_PATH)) {
            try {
                var lines = Files.readAllLines(OUTPUT_PATH);
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
    public boolean shouldApply() {
        var count = countScoredForRelationships();
        if (count > 0) {
            logger.info("Database already has {} SCORED_FOR relationships - skipping apply", count);
            return false;
        }
        return true;
    }

    // ========== GENERATE ==========

    @Override
    public void generate() throws IOException {
        Files.createDirectories(OUTPUT_PATH.getParent());

        var works = entityRepository.findAll(Work.class);
        var instruments = entityRepository.findAll(Instrument.class);
        var ensembles = entityRepository.findAll(Ensemble.class);

        logger.info("Loaded {} works, {} instruments, {} ensembles",
                works.size(), instruments.size(), ensembles.size());

        // Build lookup maps
        var instrumentsByName = buildNameLookup(instruments);
        var ensemblesById = ensembles.stream()
                .collect(java.util.stream.Collectors.toMap(Ensemble::getId, e -> e));

        // Find orchestra ensemble for concerto pattern
        var orchestra = ensemblesById.get("symphony-orchestra");

        // Find piano for sonata accompaniment
        var piano = instruments.stream()
                .filter(i -> i.getId().equals("piano"))
                .findFirst()
                .orElse(null);

        try (var writer = new PrintWriter(new FileWriter(OUTPUT_PATH.toFile()))) {
            writer.println("work_id,scored_for_type,scored_for_id,is_featured,status");

            int matched = 0;
            for (var work : works) {
                var results = inferInstrumentation(work, instrumentsByName, ensemblesById, orchestra, piano);
                if (!results.isEmpty()) {
                    matched++;
                    for (var result : results) {
                        writer.println(result.toCsvLine());
                    }
                }
            }

            logger.info("Generated instrumentation for {} of {} works", matched, works.size());
        }
    }

    private <T> Map<String, T> buildNameLookup(List<T> entities) {
        var map = new HashMap<String, T>();
        for (var entity : entities) {
            if (entity instanceof Instrument i) {
                map.put(i.getName().toLowerCase(), entity);
            } else if (entity instanceof Ensemble e) {
                map.put(e.getName().toLowerCase(), entity);
            }
        }
        return map;
    }

    private List<InstrumentationRecord> inferInstrumentation(
            Work work,
            Map<String, ?> instrumentsByName,
            Map<String, Ensemble> ensemblesById,
            Ensemble orchestra,
            Instrument piano) {

        var results = new ArrayList<InstrumentationRecord>();
        String title = work.getTitle();
        if (title == null) {
            title = work.getName();
        }
        if (title == null) {
            return results;
        }

        String titleLower = title.toLowerCase();

        // Check for exact ensemble name matches first (e.g., "String Quartet No. 3")
        for (var ensemble : ensemblesById.values()) {
            if (titleLower.contains(ensemble.getName().toLowerCase())) {
                results.add(new InstrumentationRecord(
                        work.getId(), "ensemble", ensemble.getId(), false, "pending"));
            }
        }
        if (!results.isEmpty()) {
            return results;
        }

        // Keyword-based ensemble matching
        var choir = ensemblesById.get("choir");
        var chamberOrchestra = ensemblesById.get("chamber-orchestra");
        var operaCompany = ensemblesById.get("opera-company");

        // Choral works with orchestra
        if (matchesAny(titleLower, "requiem", "mass in", "missa", "cantata", "oratorio",
                "magnificat", "stabat mater", "te deum", "passion", "messiah")) {
            if (choir != null) {
                results.add(new InstrumentationRecord(
                        work.getId(), "ensemble", choir.getId(), false, "pending"));
            }
            if (orchestra != null) {
                results.add(new InstrumentationRecord(
                        work.getId(), "ensemble", orchestra.getId(), false, "pending"));
            }
            return results;
        }

        // Opera
        if (matchesAny(titleLower, "opera", "oper", "opéra")) {
            if (operaCompany != null) {
                results.add(new InstrumentationRecord(
                        work.getId(), "ensemble", operaCompany.getId(), false, "pending"));
            }
            if (orchestra != null) {
                results.add(new InstrumentationRecord(
                        work.getId(), "ensemble", orchestra.getId(), false, "pending"));
            }
            return results;
        }

        // Orchestral works (no soloist)
        if (matchesAny(titleLower, "symphony", "symphonie", "sinfonia", "overture",
                "ouverture", "tone poem", "symphonic poem", "ballet", "suite for orchestra",
                "orchestral suite", "variations for orchestra")) {
            if (orchestra != null) {
                results.add(new InstrumentationRecord(
                        work.getId(), "ensemble", orchestra.getId(), false, "pending"));
            }
            return results;
        }

        // Concerto grosso -> chamber orchestra
        if (titleLower.contains("concerto grosso") && chamberOrchestra != null) {
            results.add(new InstrumentationRecord(
                    work.getId(), "ensemble", chamberOrchestra.getId(), false, "pending"));
            return results;
        }

        // Check for instrument + pattern matches
        boolean isConcerto = titleLower.contains("concerto");
        boolean isSonata = titleLower.contains("sonata");

        for (var entry : instrumentsByName.entrySet()) {
            String instName = entry.getKey();
            if (titleLower.contains(instName)) {
                var instrument = (Instrument) entry.getValue();

                if (isConcerto && orchestra != null) {
                    // Concerto: orchestra + featured soloist
                    results.add(new InstrumentationRecord(
                            work.getId(), "ensemble", orchestra.getId(), false, "pending"));
                    results.add(new InstrumentationRecord(
                            work.getId(), "instrument", instrument.getId(), false, "pending"));
                    results.add(new InstrumentationRecord(
                            work.getId(), "instrument", instrument.getId(), true, "pending"));
                } else if (isSonata) {
                    // Sonata: instrument (+ piano if not piano sonata)
                    results.add(new InstrumentationRecord(
                            work.getId(), "instrument", instrument.getId(), false, "pending"));
                    if (piano != null && !instrument.getId().equals("piano")) {
                        results.add(new InstrumentationRecord(
                                work.getId(), "instrument", piano.getId(), false, "pending"));
                    }
                } else {
                    // Other: just the instrument
                    results.add(new InstrumentationRecord(
                            work.getId(), "instrument", instrument.getId(), false, "pending"));
                }
                break; // Only match first instrument to avoid duplicates
            }
        }

        return results;
    }

    private boolean matchesAny(String text, String... keywords) {
        for (var keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ========== APPLY ==========

    @Override
    @Transactional
    public ApplyResult apply(boolean ignoreStatus) throws IOException {
        if (!Files.exists(OUTPUT_PATH)) {
            logger.warn("CSV file not found: {}", OUTPUT_PATH);
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
                createRelationship(record);
                loaded++;
            } catch (Exception e) {
                logger.warn("Failed to create relationship for {} -> {}: {}",
                        record.workId(), record.targetId(), e.getMessage());
                failed++;
            }
        }

        logger.info("Applied {} instrumentation relationships ({} skipped, {} failed)", loaded, skipped, failed);
        return new ApplyResult(loaded, skipped, failed);
    }

    private List<InstrumentationRecord> readCsv() throws IOException {
        var records = new ArrayList<InstrumentationRecord>();

        try (var reader = new BufferedReader(new FileReader(OUTPUT_PATH.toFile()))) {
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }

                var parts = line.split(",");
                if (parts.length >= 5) {
                    records.add(new InstrumentationRecord(
                            parts[0],
                            parts[1],
                            parts[2],
                            Boolean.parseBoolean(parts[3]),
                            parts[4]
                    ));
                }
            }
        }

        return records;
    }

    private void createRelationship(InstrumentationRecord record) {
        if (record.isFeatured()) {
            // FEATURES relationship (only for instruments)
            var query = """
                    MATCH (w:Work {id: $workId}), (i:Instrument {id: $targetId})
                    MERGE (w)-[:FEATURES]->(i)
                    """;
            persistenceManager.execute(
                    QuerySpecification.withStatement(query)
                            .bind(Map.of("workId", record.workId(), "targetId", record.targetId()))
            );
        } else if ("ensemble".equals(record.targetType())) {
            // SCORED_FOR -> Ensemble
            var query = """
                    MATCH (w:Work {id: $workId}), (e:Ensemble {id: $targetId})
                    MERGE (w)-[:SCORED_FOR]->(e)
                    """;
            persistenceManager.execute(
                    QuerySpecification.withStatement(query)
                            .bind(Map.of("workId", record.workId(), "targetId", record.targetId()))
            );
        } else {
            // SCORED_FOR -> Instrument
            var query = """
                    MATCH (w:Work {id: $workId}), (i:Instrument {id: $targetId})
                    MERGE (w)-[:SCORED_FOR]->(i)
                    """;
            persistenceManager.execute(
                    QuerySpecification.withStatement(query)
                            .bind(Map.of("workId", record.workId(), "targetId", record.targetId()))
            );
        }
    }

    public long countScoredForRelationships() {
        var query = "MATCH (:Work)-[r:SCORED_FOR]->() RETURN count(r) AS count";
        return persistenceManager.getOne(
                QuerySpecification.withStatement(query).transform(Long.class)
        );
    }

    @Transactional
    public long deleteAllRelationships() {
        var scoredFor = countScoredForRelationships();
        var features = countFeaturesRelationships();

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (:Work)-[r:SCORED_FOR]->() DELETE r"));
        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (:Work)-[r:FEATURES]->() DELETE r"));

        var total = scoredFor + features;
        logger.info("Deleted {} SCORED_FOR and {} FEATURES relationships", scoredFor, features);
        return total;
    }

    private long countFeaturesRelationships() {
        var query = "MATCH (:Work)-[r:FEATURES]->() RETURN count(r) AS count";
        return persistenceManager.getOne(
                QuerySpecification.withStatement(query).transform(Long.class)
        );
    }

    record InstrumentationRecord(
            String workId,
            String targetType,  // "instrument" or "ensemble"
            String targetId,
            boolean isFeatured,
            String status
    ) {
        public String toCsvLine() {
            return String.format("%s,%s,%s,%s,%s",
                    workId, targetType, targetId, isFeatured, status);
        }
    }
}
