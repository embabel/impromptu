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

    private static final String OPEN_OPUS_API = "https://api.openopus.org/work/dump.json";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9-]");

    private final PersistenceManager persistenceManager;
    private final ObjectMapper objectMapper;

    public OpenOpusController(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        this.persistenceManager = persistenceManager;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/load", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody load() {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            try {
                writer.println("Fetching data from Open Opus API...");
                writer.flush();

                try (InputStream is = URI.create(OPEN_OPUS_API).toURL().openStream()) {
                    OpenOpusDump dump = objectMapper.readValue(is, OpenOpusDump.class);
                    loadData(dump, writer);
                }

                writer.println("Done!");
            } catch (Exception e) {
                writer.println("Error: " + e.getMessage());
            }
        };
    }

    @DeleteMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody delete() {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            writer.println("Deleting all Open Opus data...");
            writer.flush();

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (c:Composer)-[r:COMPOSED]->(w:Work) DELETE r"));
            writer.println("  Deleted COMPOSED relationships");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (w:Work)-[r:OF_GENRE]->(g:Genre) DELETE r"));
            writer.println("  Deleted OF_GENRE relationships");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (c:Composer)-[r:OF_EPOCH]->(e:Epoch) DELETE r"));
            writer.println("  Deleted OF_EPOCH relationships");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (w:Work) DELETE w"));
            writer.println("  Deleted Work nodes");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (c:Composer) DELETE c"));
            writer.println("  Deleted Composer nodes");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (g:Genre) DELETE g"));
            writer.println("  Deleted Genre nodes");

            persistenceManager.execute(QuerySpecification.withStatement(
                    "MATCH (e:Epoch) DELETE e"));
            writer.println("  Deleted Epoch nodes");

            writer.println("Done!");
        };
    }

    private void loadData(OpenOpusDump dump, PrintWriter writer) {
        List<OpenOpusComposer> composers = dump.composers();
        if (composers == null || composers.isEmpty()) {
            writer.println("No composers found in dump");
            return;
        }

        writer.println("Found " + composers.size() + " composers");
        writer.flush();

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

        writer.println("Found " + workCount + " works, " + epochs.size() + " epochs, " + genres.size() + " genres");
        writer.flush();

        // Create epochs
        createEpochs(epochs);
        writer.println("Created " + epochs.size() + " epochs");
        writer.flush();

        // Create genres
        createGenres(genres);
        writer.println("Created " + genres.size() + " genres");
        writer.flush();

        // Create composers
        createComposers(composers);
        writer.println("Created " + composers.size() + " composers");
        writer.flush();

        // Create works in batches
        int worksCreated = createWorks(composers, writer);
        writer.println("Created " + worksCreated + " works total");
        writer.flush();
    }

    private void createEpochs(Set<String> epochs) {
        if (epochs.isEmpty()) return;

        List<Map<String, Object>> epochData = epochs.stream()
                .map(name -> Map.<String, Object>of("id", toId(name), "name", name))
                .toList();

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $epochs AS epoch
                        MERGE (e:Epoch {id: epoch.id})
                        SET e.name = epoch.name
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
                        MERGE (g:Genre {id: genre.id})
                        SET g.name = genre.name
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
                        MERGE (composer:Composer {id: c.id})
                        SET composer.name = c.name,
                            composer.completeName = c.completeName,
                            composer.birth = c.birth,
                            composer.death = c.death,
                            composer.popular = c.popular,
                            composer.recommended = c.recommended
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
                            MATCH (composer:Composer {id: w.composerId})
                            MERGE (work:Work {id: w.id})
                            SET work.title = w.title,
                                work.subtitle = w.subtitle,
                                work.searchTerms = w.searchTerms,
                                work.popular = w.popular,
                                work.recommended = w.recommended
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

            writer.println("  Created works " + i + "-" + Math.min(i + batchSize, workData.size()));
            writer.flush();
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
