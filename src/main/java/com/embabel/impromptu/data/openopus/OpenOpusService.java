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
package com.embabel.impromptu.data.openopus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Service for loading Open Opus composer and work data into Neo4j.
 */
@Service
public class OpenOpusService {

    private static final Logger logger = LoggerFactory.getLogger(OpenOpusService.class);
    private static final String OPEN_OPUS_API = "https://api.openopus.org/work/dump.json";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9-]");

    private final PersistenceManager persistenceManager;
    private final ObjectMapper objectMapper;

    public OpenOpusService(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        this.persistenceManager = persistenceManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Check if Open Opus data has already been loaded.
     */
    public boolean hasData() {
        var counts = persistenceManager.query(
                QuerySpecification.withStatement(
                        "MATCH (c:Composer {primarySource: 'openopus'}) RETURN count(c) AS count"
                ).transform(Long.class)
        );
        return !counts.isEmpty() && counts.getFirst() > 0;
    }

    /**
     * Load data from Open Opus API.
     *
     * @param logConsumer consumer for progress messages (can be used for streaming to client or logging)
     * @return summary of what was loaded
     */
    public LoadResult load(Consumer<String> logConsumer) {
        logConsumer.accept("Fetching data from Open Opus API...");

        try (InputStream is = URI.create(OPEN_OPUS_API).toURL().openStream()) {
            OpenOpusDump dump = objectMapper.readValue(is, OpenOpusDump.class);
            return loadData(dump, logConsumer);
        } catch (Exception e) {
            logger.error("OpenOpus load failed", e);
            throw new RuntimeException("Failed to load Open Opus data: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all Open Opus data.
     *
     * @param logConsumer consumer for progress messages
     */
    public void delete(Consumer<String> logConsumer) {
        logConsumer.accept("Deleting Open Opus data (primarySource='openopus')...");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (c:Composer {primarySource: 'openopus'})-[r:COMPOSED]->(w:Work) DELETE r"));
        logConsumer.accept("  Deleted COMPOSED relationships");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (w:Work {primarySource: 'openopus'})-[r:OF_GENRE]->(g:Genre) DELETE r"));
        logConsumer.accept("  Deleted OF_GENRE relationships");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (c:Composer {primarySource: 'openopus'})-[r:OF_EPOCH]->(e:Epoch) DELETE r"));
        logConsumer.accept("  Deleted OF_EPOCH relationships");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (w:Work {primarySource: 'openopus'}) DELETE w"));
        logConsumer.accept("  Deleted Work nodes");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (c:Composer {primarySource: 'openopus'}) DELETE c"));
        logConsumer.accept("  Deleted Composer nodes");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (g:Genre {primarySource: 'openopus'}) DELETE g"));
        logConsumer.accept("  Deleted Genre nodes");

        persistenceManager.execute(QuerySpecification.withStatement(
                "MATCH (e:Epoch {primarySource: 'openopus'}) DELETE e"));
        logConsumer.accept("  Deleted Epoch nodes");

        logConsumer.accept("Done!");
    }

    private LoadResult loadData(OpenOpusDump dump, Consumer<String> logConsumer) {
        List<OpenOpusComposer> composers = dump.composers();
        if (composers == null || composers.isEmpty()) {
            logConsumer.accept("No composers found in dump");
            return new LoadResult(0, 0, 0, 0);
        }

        logConsumer.accept("Found " + composers.size() + " composers");

        // Collect unique epochs and genres
        Set<String> epochs = new HashSet<>();
        Set<String> genres = new HashSet<>();
        int workCount = 0;

        for (OpenOpusComposer composer : composers) {
            if (composer.epoch() != null && !composer.epoch().isBlank()) {
                epochs.add(composer.epoch());
            }
            if (composer.works() != null) {
                for (OpenOpusWork work : composer.works()) {
                    workCount++;
                    if (work.genre() != null && !work.genre().isBlank()) {
                        genres.add(work.genre());
                    }
                }
            }
        }

        logConsumer.accept("Found " + workCount + " works, " + epochs.size() + " epochs, " + genres.size() + " genres");

        // Create epochs
        createEpochs(epochs);
        logConsumer.accept("Created " + epochs.size() + " epochs");

        // Create genres
        createGenres(genres);
        logConsumer.accept("Created " + genres.size() + " genres");

        // Create composers
        createComposers(composers);
        logConsumer.accept("Created " + composers.size() + " composers");

        // Create works in batches
        int worksCreated = createWorks(composers, logConsumer);
        logConsumer.accept("Created " + worksCreated + " works total");

        return new LoadResult(composers.size(), worksCreated, epochs.size(), genres.size());
    }

    private void createEpochs(Set<String> epochs) {
        if (epochs.isEmpty()) return;

        List<Map<String, Object>> epochData = epochs.stream()
                .map(name -> Map.<String, Object>of("id", toId(name), "name", name))
                .toList();

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                                UNWIND $epochs AS epoch
                                MERGE (e:__Entity__:Epoch:Reference {id: epoch.id})
                                SET e.name = epoch.name,
                                    e.primarySource = "openopus"
                                """)
                        .bind(Map.of("epochs", epochData))
        );
    }

    private void createGenres(Set<String> genres) {
        if (genres.isEmpty()) return;

        List<Map<String, Object>> genreData = genres.stream()
                .map(name -> Map.<String, Object>of("id", toId(name), "name", name))
                .toList();

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                                UNWIND $genres AS genre
                                MERGE (g:__Entity__:Genre:Reference {id: genre.id})
                                SET g.name = genre.name,
                                    g.primarySource = "openopus"
                                """)
                        .bind(Map.of("genres", genreData))
        );
    }

    private void createComposers(List<OpenOpusComposer> composers) {
        List<Map<String, Object>> composerData = new ArrayList<>();

        for (OpenOpusComposer composer : composers) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", toComposerId(composer));
            data.put("name", composer.name());
            data.put("completeName", composer.completeName());
            data.put("birthYear", parseYear(composer.birth()));
            data.put("deathYear", parseYear(composer.death()));
            data.put("popular", composer.isPopular());
            data.put("recommended", composer.isRecommended());
            data.put("epochId", composer.epoch() != null ? toId(composer.epoch()) : null);
            composerData.add(data);
        }

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                                UNWIND $composers AS c
                                MERGE (composer:__Entity__:Composer:Reference {id: c.id})
                                SET composer.name = c.name,
                                    composer.completeName = c.completeName,
                                    composer.birthYear = c.birthYear,
                                    composer.deathYear = c.deathYear,
                                    composer.popular = c.popular,
                                    composer.recommended = c.recommended,
                                    composer.primarySource = "openopus"
                                """)
                        .bind(Map.of("composers", composerData))
        );

        List<Map<String, Object>> composersWithEpoch = composerData.stream()
                .filter(c -> c.get("epochId") != null)
                .toList();

        if (!composersWithEpoch.isEmpty()) {
            persistenceManager.execute(
                    QuerySpecification.withStatement("""
                                    UNWIND $composers AS c
                                    MATCH (composer:Composer {id: c.id})
                                    MATCH (epoch:Epoch {id: c.epochId})
                                    MERGE (composer)-[:OF_EPOCH]->(epoch)
                                    """)
                            .bind(Map.of("composers", composersWithEpoch))
            );
        }
    }

    private int createWorks(List<OpenOpusComposer> composers, Consumer<String> logConsumer) {
        List<Map<String, Object>> workData = new ArrayList<>();

        for (OpenOpusComposer composer : composers) {
            if (composer.works() == null) continue;

            String composerId = toComposerId(composer);
            String composerName = composer.name();
            int workIndex = 0;

            for (OpenOpusWork work : composer.works()) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", toWorkId(composer, work, workIndex++));
                data.put("title", work.title());
                data.put("subtitle", work.subtitle());
                data.put("description", buildWorkDescription(composerName, work));
                data.put("searchTerms", work.searchTerms());
                data.put("popular", work.isPopular());
                data.put("recommended", work.isRecommended());
                data.put("composerId", composerId);
                data.put("genreId", work.genre() != null && !work.genre().isBlank()
                        ? toId(work.genre()) : null);
                workData.add(data);
            }
        }

        if (workData.isEmpty()) return 0;

        int batchSize = 200;
        for (int i = 0; i < workData.size(); i += batchSize) {
            List<Map<String, Object>> batch = workData.subList(i, Math.min(i + batchSize, workData.size()));

            persistenceManager.execute(
                    QuerySpecification.withStatement("""
                                    UNWIND $works AS w
                                    MATCH (composer:__Entity__:Composer:Reference {id: w.composerId})
                                    MERGE (work:__Entity__:Work:Reference {id: w.id})
                                    SET work.name = w.title,
                                        work.title = w.title,
                                        work.subtitle = w.subtitle,
                                        work.description = w.description,
                                        work.searchTerms = w.searchTerms,
                                        work.popular = w.popular,
                                        work.recommended = w.recommended,
                                        work.primarySource = "openopus"
                                    MERGE (composer)-[:COMPOSED]->(work)
                                    """)
                            .bind(Map.of("works", batch))
            );

            List<Map<String, Object>> worksWithGenre = batch.stream()
                    .filter(w -> w.get("genreId") != null)
                    .toList();

            if (!worksWithGenre.isEmpty()) {
                persistenceManager.execute(
                        QuerySpecification.withStatement("""
                                        UNWIND $works AS w
                                        MATCH (work:Work {id: w.id})
                                        MATCH (genre:Genre {id: w.genreId})
                                        MERGE (work)-[:OF_GENRE]->(genre)
                                        """)
                                .bind(Map.of("works", worksWithGenre))
                );
            }

            logConsumer.accept("  Created works " + i + "-" + Math.min(i + batchSize, workData.size()));
        }

        return workData.size();
    }

    private String toId(String name) {
        String normalized = Normalizer.normalize(name.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return NON_ALPHANUMERIC.matcher(normalized.replace(" ", "-")).replaceAll("");
    }

    private String toComposerId(OpenOpusComposer composer) {
        String base = toId(composer.completeName());
        if (composer.birth() != null && composer.birth().length() >= 4) {
            base += "-" + composer.birth().substring(0, 4);
        }
        return base;
    }

    private String toWorkId(OpenOpusComposer composer, OpenOpusWork work, int index) {
        return toComposerId(composer) + "-" + toId(work.title()) + "-" + index;
    }

    private String buildWorkDescription(String composerName, OpenOpusWork work) {
        var sb = new StringBuilder();
        sb.append(composerName);
        sb.append(" - ");
        sb.append(work.title());
        if (work.subtitle() != null && !work.subtitle().isBlank()) {
            sb.append(", ").append(work.subtitle());
        }
        return sb.toString();
    }

    private Long parseYear(String yearStr) {
        if (yearStr == null || yearStr.isBlank()) {
            return null;
        }
        try {
            String year = yearStr.length() >= 4 ? yearStr.substring(0, 4) : yearStr;
            return Long.parseLong(year);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse year: {}", yearStr);
            return null;
        }
    }

    /**
     * Result of a load operation.
     */
    public record LoadResult(int composers, int works, int epochs, int genres) {
        @Override
        public String toString() {
            return String.format("%d composers, %d works, %d epochs, %d genres", composers, works, epochs, genres);
        }
    }
}
