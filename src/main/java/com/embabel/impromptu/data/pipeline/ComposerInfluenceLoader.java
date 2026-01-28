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

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads approved composer influence relationships from CSV into Neo4j.
 */
@Service
public class ComposerInfluenceLoader {

    private static final Logger logger = LoggerFactory.getLogger(ComposerInfluenceLoader.class);

    private final PersistenceManager persistenceManager;

    public record InfluenceRecord(
            String from,
            String to,
            String reason,
            double strength,
            double confidence,
            double divergence,
            String status
    ) {
    }

    public record LoadResult(int loaded, int skipped, int notFound) {
    }

    public ComposerInfluenceLoader(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Parse CSV file and return all records.
     */
    public List<InfluenceRecord> readCsv() throws IOException {
        var records = new ArrayList<InfluenceRecord>();

        try (var reader = new BufferedReader(new FileReader(GeneralKnowledgeLlmInfluenceComposerEnhancer.INFLUENCES_CSV_PATH.toFile()))) {
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }

                var record = parseCsvLine(line);
                if (record != null) {
                    records.add(record);
                }
            }
        }

        return records;
    }

    private InfluenceRecord parseCsvLine(String line) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        if (fields.size() < 7) {
            logger.warn("Invalid CSV line (not enough fields): {}", line);
            return null;
        }

        try {
            return new InfluenceRecord(
                    fields.get(0),
                    fields.get(1),
                    fields.get(2),
                    Double.parseDouble(fields.get(3)),
                    Double.parseDouble(fields.get(4)),
                    Double.parseDouble(fields.get(5)),
                    fields.get(6)
            );
        } catch (NumberFormatException e) {
            logger.warn("Invalid CSV line (number parse error): {}", line);
            return null;
        }
    }

    /**
     * Load approved influence relationships into Neo4j.
     */
    @Transactional
    public LoadResult loadApproved() throws IOException {
        var records = readCsv();
        var approved = records.stream()
                .filter(r -> "approved".equalsIgnoreCase(r.status()))
                .toList();

        logger.info("Found {} approved relationships out of {} total", approved.size(), records.size());
        return loadRecords(approved);
    }

    /**
     * Load ALL influence relationships into Neo4j, regardless of status.
     * Use with caution - this includes unreviewed/pending relationships.
     */
    @Transactional
    public LoadResult loadAll() throws IOException {
        var records = readCsv();
        logger.info("Loading ALL {} relationships (including unreviewed)", records.size());
        return loadRecords(records);
    }

    private LoadResult loadRecords(List<InfluenceRecord> records) {
        int loaded = 0;
        int skipped = 0;
        int notFound = 0;

        for (var record : records) {
            var existsQuery = """
                    MATCH (from:Composer {id: $fromId})-[r:INFLUENCED]->(to:Composer {id: $toId})
                    RETURN count(r) AS count
                    """;
            var existsResult = persistenceManager.getOne(
                    QuerySpecification.withStatement(existsQuery)
                            .bind(Map.of("fromId", record.from(), "toId", record.to()))
                            .transform(Long.class)
            );

            if (existsResult > 0) {
                skipped++;
                continue;
            }

            var createQuery = """
                    MATCH (from:Composer {id: $fromId})
                    MATCH (to:Composer {id: $toId})
                    CREATE (from)-[r:INFLUENCED {
                        reason: $reason,
                        strength: $strength,
                        confidence: $confidence,
                        divergence: $divergence,
                        source: 'llm-generated',
                        reviewedAt: datetime()
                    }]->(to)
                    RETURN count(r) AS created
                    """;

            try {
                var created = persistenceManager.getOne(
                        QuerySpecification.withStatement(createQuery)
                                .bind(Map.of(
                                        "fromId", record.from(),
                                        "toId", record.to(),
                                        "reason", record.reason(),
                                        "strength", record.strength(),
                                        "confidence", record.confidence(),
                                        "divergence", record.divergence()
                                ))
                                .transform(Long.class)
                );

                if (created > 0) {
                    loaded++;
                    logger.debug("Created: {} -> {}", record.from(), record.to());
                } else {
                    notFound++;
                    logger.warn("Composers not found: {} -> {}", record.from(), record.to());
                }
            } catch (Exception e) {
                notFound++;
                logger.warn("Failed to create {} -> {}: {}", record.from(), record.to(), e.getMessage());
            }
        }

        logger.info("Load complete: {} loaded, {} skipped (existing), {} not found",
                loaded, skipped, notFound);

        return new LoadResult(loaded, skipped, notFound);
    }

    /**
     * Get statistics about current influence relationships in the database.
     */
    public Map<String, Object> getStats() {
        var total = countInfluenceRelationships();
        // For now, just return total - detailed stats can be added later if needed
        return Map.of(
                "totalRelationships", total,
                "uniqueInfluencers", 0L,
                "uniqueInfluenced", 0L
        );
    }

    /**
     * Count the number of INFLUENCED relationships in the database.
     */
    public long countInfluenceRelationships() {
        var countQuery = """
                MATCH ()-[r:INFLUENCED]->()
                RETURN count(r) AS total
                """;

        return persistenceManager.getOne(
                QuerySpecification.withStatement(countQuery)
                        .transform(Long.class)
        );
    }

    /**
     * Delete all INFLUENCED relationships from the database.
     *
     * @return the number of relationships deleted
     */
    @Transactional
    public long deleteAllInfluences() {
        var count = countInfluenceRelationships();

        var deleteQuery = """
                MATCH ()-[r:INFLUENCED]->()
                DELETE r
                """;

        persistenceManager.execute(
                QuerySpecification.withStatement(deleteQuery)
        );

        logger.info("Deleted {} influence relationships", count);
        return count;
    }

    /**
     * Get the path to the influences CSV file.
     */
    public Path getCsvPath() {
        return GeneralKnowledgeLlmInfluenceComposerEnhancer.INFLUENCES_CSV_PATH;
    }
}
