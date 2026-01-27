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
package com.embabel.impromptu.data.influence;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.util.VisualizableTask;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.domain.Composer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Generates composer influence relationships using LLM general knowledge.
 * The LLM is prompted to identify documented musical influences between composers.
 */
@Service
public class GeneralKnowledgeLlmComposerInfluenceGenerator implements ComposerInfluenceGenerator {

    private static final Logger logger = LoggerFactory.getLogger(GeneralKnowledgeLlmComposerInfluenceGenerator.class);

    private final NamedEntityDataRepository entityRepository;
    private final Ai ai;
    private final ImpromptuProperties properties;

    /**
     * Response structure for LLM-generated influences.
     */
    record InfluenceResponse(List<GeneratedInfluence> influences) {
    }

    /**
     * Single influence relationship from LLM.
     */
    record GeneratedInfluence(
            String from,
            String to,
            String reason,
            double strength,
            double confidence,
            double divergence
    ) {
    }

    public GeneralKnowledgeLlmComposerInfluenceGenerator(
            NamedEntityDataRepository entityRepository,
            AiBuilder aiBuilder,
            ImpromptuProperties properties) {
        this.entityRepository = entityRepository;
        this.ai = aiBuilder.ai();
        this.properties = properties;
    }

    @Override
    public List<ComposerInfo> loadComposers() {
        return entityRepository.findAll(Composer.class).stream()
                .sorted(Comparator.comparing(c -> c.getBirthYear() != null ? c.getBirthYear() : 0L))
                .map(c -> new ComposerInfo(
                        c.getId(),
                        c.getName(),
                        c.getCompleteName(),
                        c.getBirthYear(),
                        c.getDeathYear()
                ))
                .toList();
    }

    @Override
    public List<InfluenceRecord> generateInfluencesFor(ComposerInfo composer, List<ComposerInfo> allComposers) {
        // Only include composers who could have influenced this one:
        // - Born before this composer's death (or birth + 80 if death unknown)
        // - Exclude self
        long influenceCutoff = composer.deathYear() != null
                ? composer.deathYear()
                : (composer.birthYear() != null ? composer.birthYear() + 80 : Long.MAX_VALUE);

        var composerList = allComposers.stream()
                .filter(c -> !c.id().equals(composer.id()))
                .filter(c -> c.birthYear() != null && c.birthYear() < influenceCutoff)
                .map(c -> c.id() + " (" + c.completeName() + ", " + c.birthYear() + "-" + (c.deathYear() != null ? c.deathYear() : "present") + ")")
                .toList();

        try {
            var response = ai
                    .withLlm(properties.propositionExtractionLlm())
                    .withId("generate_composer_influences")
                    .withTemplate("generate_composer_influences")
                    .createObject(InfluenceResponse.class, Map.of(
                            "composerId", composer.id(),
                            "composerName", composer.completeName(),
                            "composerShortName", composer.name(),
                            "birthYear", String.valueOf(composer.birthYear()),
                            "deathYear", composer.deathYear() != null ? String.valueOf(composer.deathYear()) : "present",
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
            logger.warn("Failed to generate influences for {}: {}", composer.id(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public void generateAllInfluences() throws IOException {
        var composers = loadComposers();
        logger.info("Loaded {} composers from database", composers.size());

        Files.createDirectories(INFLUENCES_CSV_PATH.getParent());

        var allInfluences = new java.util.LinkedHashSet<String>();
        int total = composers.size();

        // Log initial progress
        logProgress(0, total);

        int processed = 0;
        for (var composer : composers) {
            var influences = generateInfluencesFor(composer, composers);
            for (var inf : influences) {
                allInfluences.add(inf.toCsvLine());
                logger.info("  {} -> {} (strength={}, confidence={}, divergence={})",
                        inf.from(), inf.to(), inf.strength(), inf.confidence(), inf.divergence());
            }

            processed++;

            // Log progress every 5 composers
            if (processed % 5 == 0 || processed == total) {
                logProgress(processed, total);
            }
        }

        try (var writer = new PrintWriter(new FileWriter(INFLUENCES_CSV_PATH.toFile()))) {
            writer.println("from,to,reason,strength,confidence,divergence,status");
            for (var line : allInfluences) {
                writer.println(line);
            }
        }

        logger.info("Wrote {} influence relationships to {}", allInfluences.size(), INFLUENCES_CSV_PATH);
    }

    private void logProgress(int current, int total) {
        var progress = VisualizableTask.Companion.invoke("Generating influences", current, total);
        logger.info(progress.createProgressBar(50));
    }
}
