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
package com.embabel.impromptu.integration;

import com.embabel.agent.rag.model.Chunk;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.SchemaRegistry;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.impromptu.TestSecurityConfiguration;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.impromptu.user.ImpromptuUserService;
import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Tag;  // Temporarily disabled
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test for RJ.txt ingestion using real LLM calls.
 * <p>
 * Uses full application context (same as real app) with @Transactional for rollback.
 * Does NOT go through the web stack - injects services directly.
 * <p>
 * Run with: {@code ./mvnw test -Dgroups=integration -Dtest=RjIngestionIT}
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Neo4j running (docker compose up -d)</li>
 *   <li>Open Opus data loaded</li>
 *   <li>Valid LLM API keys configured</li>
 * </ul>
 */
// @Tag("integration") // Temporarily disabled for testing
@SpringBootTest(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.ai.mcp.client.enabled=false",
                "impromptu.objective=test",
                "impromptu.behaviour=test",
                "impromptu.embedding-service=text-embedding-3-small"
        }
)
@Import(TestSecurityConfiguration.class)
@Transactional
class RjIngestionIT {

    @Autowired
    private PropositionPipeline propositionPipeline;

    @Autowired
    private ImpromptuUserService userService;

    @Autowired
    private SchemaRegistry schemaRegistry;

    @Autowired
    private EntityResolver entityResolver;

    @Test
    @DisplayName("Ingest rj.txt with RJ as known user - results rolled back")
    void ingestRjFile() throws IOException {
        var testId = "rj_test_" + System.currentTimeMillis();
        var contextId = "rj_context_" + testId;

        System.out.println("=".repeat(70));
        System.out.println("RJ.TXT INGESTION TEST (Transactional - will rollback)");
        System.out.println("=".repeat(70));
        System.out.println("Test ID: " + testId);
        System.out.println("Context: " + contextId);

        // Step 1: Create and persist RJ user
        System.out.println("\n--- CREATING RJ USER ---");
        var rjUser = new ImpromptuUser(testId, "RJ", "rj", "rj@test.example.com");
        userService.save(rjUser);
        System.out.println("Created user: " + rjUser);

        // Step 2: Read rj.txt
        var rjText = new ClassPathResource("data/rj.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        System.out.println("\n--- RJ.TXT CONTENT ---");
        System.out.println(rjText);
        System.out.println("--- END CONTENT ---");

        // Step 3: Build analysis context with RJ as known entity
        var knownEntity = KnownEntity.asCurrentUser(rjUser);

        var schema = schemaRegistry.getOrDefault(null);
        var context = SourceAnalysisContext
                .withContextId(contextId)
                .withEntityResolver(entityResolver)
                .withSchema(schema)
                .withKnownEntities(knownEntity);

        // Step 4: Create chunk and run the proposition pipeline (uses real LLM)
        System.out.println("\n--- RUNNING PROPOSITION EXTRACTION ---");
        System.out.println("(This uses real LLM calls - may take a minute...)");

        var chunk = Chunk.create(rjText, "rj-preferences-file");
        var result = propositionPipeline.process(List.of(chunk), context);

        // Step 5: Print results
        System.out.println("\n" + "=".repeat(70));
        System.out.println("EXTRACTION RESULTS");
        System.out.println("=".repeat(70));

        var propositions = result.getAllPropositions();
        System.out.println("Total propositions: " + propositions.size());

        int count = 0;
        for (var prop : propositions) {
            count++;
            System.out.println("\n[Proposition " + count + "]");
            System.out.println("  Text: " + prop.getText());
            System.out.println("  Confidence: " + String.format("%.2f", prop.getConfidence()));

            var mentions = prop.getMentions();
            if (!mentions.isEmpty()) {
                System.out.println("  Mentions:");
                for (var mention : mentions) {
                    var resolved = mention.getResolvedId() != null
                            ? mention.getResolvedId()
                            : "UNRESOLVED";
                    System.out.printf("    - [%s] %s -> %s%n",
                            mention.getType(),
                            mention.getSpan(),
                            resolved);
                }
            }
        }

        // Step 6: Print new entities created
        var newEntities = result.newEntities();
        if (!newEntities.isEmpty()) {
            System.out.println("\n--- NEW ENTITIES CREATED ---");
            for (var entity : newEntities) {
                System.out.printf("  - [%s] %s (id: %s)%n",
                        entity.labels(),
                        entity.getName(),
                        entity.getId());
            }
        }

        // Assertions
        assertFalse(propositions.isEmpty(), "Should extract propositions");

        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST COMPLETE - Transaction will rollback");
        System.out.println("=".repeat(70));
    }
}
