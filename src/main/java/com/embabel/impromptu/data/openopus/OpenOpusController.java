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
package com.embabel.impromptu.data.openopus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * REST endpoint for loading Open Opus data into Neo4j.
 * <p>
 * Usage:
 * <pre>
 * # Load data (streams progress)
 * curl -X POST http://localhost:8888/api/openopus/load
 *
 * # Clear all data
 * curl -X DELETE http://localhost:8888/api/openopus
 * </pre>
 */
@RestController
@RequestMapping("/api/openopus")
public class OpenOpusController {

    private static final Logger logger = LoggerFactory.getLogger(OpenOpusController.class);
    private static final String OPEN_OPUS_API = "https://api.openopus.org/work/dump.json";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9-]");

    private final PersistenceManager persistenceManager;
    private final ObjectMapper objectMapper;

    public OpenOpusController(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        this.persistenceManager = persistenceManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Log to both server and stream to client.
     */
    private void log(PrintWriter writer, String message) {
        logger.info(message);
        writer.println(message);
        writer.flush();
    }

    @PostMapping(value = "/load", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody load() {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            try {
                log(writer, "Fetching data from Open Opus API...");

                try (InputStream is = URI.create(OPEN_OPUS_API).toURL().openStream()) {
                    OpenOpusDump dump = objectMapper.readValue(is, OpenOpusDump.class);
                    loadData(dump, writer);
                }

                log(writer, "Done!");
            } catch (Exception e) {
                log(writer, "Error: " + e.getMessage());
                logger.error("OpenOpus load failed", e);
            }
        };
    }

    @DeleteMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody delete() {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            log(writer, "Deleting all Open Opus data...");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (c:Composer)-[r:COMPOSED]->(w:Work) DELETE r"));
            log(writer, "  Deleted COMPOSED relationships");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (w:Work)-[r:OF_GENRE]->(g:Genre) DELETE r"));
            log(writer, "  Deleted OF_GENRE relationships");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (c:Composer)-[r:OF_EPOCH]->(e:Epoch) DELETE r"));
            log(writer, "  Deleted OF_EPOCH relationships");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (w:Work) DELETE w"));
            log(writer, "  Deleted Work nodes");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (c:Composer) DELETE c"));
            log(writer, "  Deleted Composer nodes");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (g:Genre) DELETE g"));
            log(writer, "  Deleted Genre nodes");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (e:Epoch) DELETE e"));
            log(writer, "  Deleted Epoch nodes");

            log(writer, "Done!");
        };
    }

    private void loadData(OpenOpusDump dump, PrintWriter writer) {
        List<OpenOpusComposer> composers = dump.composers();
        if (composers == null || composers.isEmpty()) {
            log(writer, "No composers found in dump");
            return;
        }

        log(writer, "Found " + composers.size() + " composers");

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

        log(writer, "Found " + workCount + " works, " + epochs.size() + " epochs, " + genres.size() + " genres");

        // Create epochs
        createEpochs(epochs);
        log(writer, "Created " + epochs.size() + " epochs");

        // Create genres
        createGenres(genres);
        log(writer, "Created " + genres.size() + " genres");

        // Create composers
        createComposers(composers);
        log(writer, "Created " + composers.size() + " composers");

        // Create works in batches
        int worksCreated = createWorks(composers, writer);
        log(writer, "Created " + worksCreated + " works total");
    }

    private void createEpochs(Set<String> epochs) {
        if (epochs.isEmpty()) return;

        List<Map<String, Object>> epochData = epochs.stream()
                .map(name -> Map.<String, Object>of("id", toId(name), "name", name))
                .toList();

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $epochs AS epoch
                        MERGE (e:Entity:Epoch:Reference {id: epoch.id})
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
                        MERGE (g:Entity:Genre:Reference {id: genre.id})
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
            data.put("birth", composer.birth());
            data.put("death", composer.death());
            data.put("popular", composer.isPopular());
            data.put("recommended", composer.isRecommended());
            data.put("epochId", composer.epoch() != null ? toId(composer.epoch()) : null);
            composerData.add(data);
        }

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $composers AS c
                        MERGE (composer:Entity:Composer:Reference {id: c.id})
                        SET composer.name = c.name,
                            composer.completeName = c.completeName,
                            composer.birth = c.birth,
                            composer.death = c.death,
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

    private int createWorks(List<OpenOpusComposer> composers, PrintWriter writer) {
        List<Map<String, Object>> workData = new ArrayList<>();

        for (OpenOpusComposer composer : composers) {
            if (composer.works() == null) continue;

            String composerId = toComposerId(composer);
            int workIndex = 0;

            for (OpenOpusWork work : composer.works()) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", toWorkId(composer, work, workIndex++));
                data.put("title", work.title());
                data.put("subtitle", work.subtitle());
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

        int batchSize = 1000;
        for (int i = 0; i < workData.size(); i += batchSize) {
            List<Map<String, Object>> batch = workData.subList(i, Math.min(i + batchSize, workData.size()));

            persistenceManager.execute(
                    QuerySpecification.withStatement("""
                            UNWIND $works AS w
                            MATCH (composer:Entity:Composer:Reference {id: w.composerId})
                            MERGE (work:Entity:Work:Reference {id: w.id})
                            SET work.title = w.title,
                                work.subtitle = w.subtitle,
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

            log(writer, "  Created works " + i + "-" + Math.min(i + batchSize, workData.size()));
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
}
