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

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.Relations;
import com.embabel.dice.common.SchemaRegistry;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.incremental.ChunkHistoryStore;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.pipeline.PropositionResults;
import com.embabel.dice.projection.graph.GraphProjector;
import com.embabel.dice.projection.graph.GraphRelationshipPersister;
import com.embabel.dice.projection.graph.ProjectedRelationship;
import com.embabel.dice.projection.graph.RelationshipPersistenceResult;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.ProjectionResults;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.TestSecurityConfiguration;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.impromptu.user.ImpromptuUserService;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired
    private Relations relations;

    @Autowired
    private DataDictionary dataDictionary;

    @Autowired
    private GraphProjector graphProjector;

    @Autowired
    private GraphRelationshipPersister graphRelationshipPersister;

    @Autowired
    private PropositionRepository propositionRepository;

    @Autowired
    private NamedEntityDataRepository entityRepository;

    @Autowired
    private ChunkHistoryStore chunkHistoryStore;

    @Autowired
    private ImpromptuProperties impromptuProperties;

    @Autowired
    private PersistenceManager persistenceManager;

    /**
     * Result of a document ingestion, capturing all extracted and persisted data.
     */
    public record IngestionResult(
            String contextId,
            ImpromptuUser user,
            PropositionResults extractionResult,
            ProjectionResults<ProjectedRelationship> projectionResults,
            RelationshipPersistenceResult persistenceResult,
            List<Proposition> persistedPropositions,
            List<String> persistedRelationships,
            List<String> persistedEntities
    ) {}

    /**
     * Generic document ingestion method that can be reused with different documents.
     * Performs the full pipeline: extraction, entity resolution, proposition persistence,
     * graph projection, and relationship persistence.
     *
     * @param documentContent the text content to ingest
     * @param sourceName      identifier for the document source
     * @param user            the user context for the ingestion
     * @param testIdPrefix    prefix for generating unique test/context IDs
     * @return IngestionResult containing all extracted and persisted data
     */
    private IngestionResult ingestDocument(
            String documentContent,
            String sourceName,
            ImpromptuUser user,
            String testIdPrefix) {

        var testId = testIdPrefix + "_" + System.currentTimeMillis();
        var contextId = testIdPrefix + "_context_" + testId;

        System.out.println("=" .repeat(70));
        System.out.println("DOCUMENT INGESTION: " + sourceName);
        System.out.println("=" .repeat(70));
        System.out.println("Test ID: " + testId);
        System.out.println("Context: " + contextId);
        System.out.println("User: " + user.getDisplayName());

        // Persist user
        userService.save(user);

        // Build analysis context
        var knownEntity = KnownEntity.asCurrentUser(user);
        var schema = schemaRegistry.getOrDefault(null);
        var context = SourceAnalysisContext
                .withContextId(contextId)
                .withEntityResolver(entityResolver)
                .withSchema(schema)
                .withRelations(relations)
                .withKnownEntities(knownEntity);

        System.out.println("Relations count: " + context.getRelations().size());

        // Step 1: Run proposition extraction pipeline
        System.out.println("\n--- RUNNING PROPOSITION EXTRACTION ---");
        var chunk = Chunk.create(documentContent, sourceName);
        var extractionResult = propositionPipeline.process(List.of(chunk), context);

        var propositions = extractionResult.getAllPropositions();
        System.out.println("Extracted " + propositions.size() + " propositions");

        // Step 2: Persist propositions and entities
        System.out.println("\n--- PERSISTING PROPOSITIONS AND ENTITIES ---");
        extractionResult.persist(propositionRepository, entityRepository);

        // Step 3: Run graph projection
        System.out.println("\n--- RUNNING GRAPH PROJECTION ---");
        var propsToProject = extractionResult.propositionsToPersist();
        ProjectionResults<ProjectedRelationship> projectionResults = graphProjector.projectAll(propsToProject, dataDictionary);

        System.out.println("Projected: " + projectionResults.getProjected().size() + " relationships");
        System.out.println("Skipped: " + projectionResults.getSkipped().size());

        // Step 4: Persist relationships
        RelationshipPersistenceResult persistenceResult = null;
        if (!projectionResults.getProjected().isEmpty()) {
            System.out.println("\n--- PERSISTING RELATIONSHIPS ---");
            persistenceResult = graphRelationshipPersister.persist(projectionResults);
            System.out.println("Persisted: " + persistenceResult.getPersistedCount() + " relationships");
        }

        // Step 5: Query Neo4j to verify persistence
        System.out.println("\n--- VERIFYING NEO4J PERSISTENCE ---");

        // Query persisted propositions
        var persistedPropositions = queryPropositionsByContext(contextId);
        System.out.println("Propositions in Neo4j: " + persistedPropositions.size());

        // Query persisted relationships
        var persistedRelationships = queryRelationshipsByUser(user.getId());
        System.out.println("User relationships in Neo4j: " + persistedRelationships.size());
        for (var rel : persistedRelationships) {
            System.out.println("  - " + rel);
        }

        // Query persisted entities created in this context
        var persistedEntities = queryEntitiesCreatedInContext(contextId);
        System.out.println("Entities referenced in Neo4j: " + persistedEntities.size());
        for (var entity : persistedEntities) {
            System.out.println("  - " + entity);
        }

        // Step 6: Verify and assert results
        System.out.println("\n--- VERIFICATION ---");

        // Verify propositions were extracted
        assertFalse(propositions.isEmpty(), "Should extract at least one proposition");
        System.out.println("✓ Propositions extracted: " + propositions.size());

        // Verify propositions were persisted to Neo4j
        assertFalse(persistedPropositions.isEmpty(), "Propositions should be persisted to Neo4j");
        assertEquals(propositions.size(), persistedPropositions.size(),
                "All extracted propositions should be persisted");
        System.out.println("✓ Propositions persisted to Neo4j: " + persistedPropositions.size());

        // Verify context ID is correct
        assertEquals(contextId, persistedPropositions.get(0).getContextIdValue(),
                "Proposition should have the correct context ID");
        System.out.println("✓ Context ID verified: " + contextId);

        // Verify relationships if any were projected
        if (!projectionResults.getProjected().isEmpty()) {
            assertNotNull(persistenceResult, "Persistence result should not be null");
            assertTrue(persistenceResult.getPersistedCount() > 0,
                    "Should persist at least one relationship");
            assertFalse(persistedRelationships.isEmpty(),
                    "User should have relationships in Neo4j");
            System.out.println("✓ Relationships persisted: " + persistenceResult.getPersistedCount());
        } else {
            System.out.println("○ No relationships projected (predicates didn't match)");
        }

        // Print projected relationships detail
        var projected = projectionResults.getProjected();
        if (!projected.isEmpty()) {
            System.out.println("\nProjected relationships:");
            for (var rel : projected) {
                System.out.printf("  - %s -[:%s]-> %s%n",
                        rel.getSourceId(), rel.getType(), rel.getTargetId());
            }
        }

        System.out.println("\n" + "=" .repeat(70));
        System.out.println("INGESTION COMPLETE - Transaction will rollback");
        System.out.println("=" .repeat(70));

        return new IngestionResult(
                contextId,
                user,
                extractionResult,
                projectionResults,
                persistenceResult,
                persistedPropositions,
                persistedRelationships,
                persistedEntities
        );
    }

    /**
     * Query propositions by context ID from Neo4j.
     */
    private List<Proposition> queryPropositionsByContext(String contextId) {
        var cypher = """
                MATCH (p:Proposition)
                WHERE p.contextId = $contextId
                RETURN p.id AS id
                """;
        var ids = persistenceManager.query(
                QuerySpecification.withStatement(cypher)
                        .bind(Map.of("contextId", contextId))
                        .transform(String.class)
        );
        return ids.stream()
                .map(propositionRepository::findById)
                .filter(p -> p != null)
                .toList();
    }

    /**
     * Query relationships from a specific user node.
     * Returns relationship descriptions as strings to avoid Drivine serialization issues.
     */
    private List<String> queryRelationshipsByUser(String userId) {
        var cypher = """
                MATCH (source)-[r]->(target)
                WHERE source.id = $userId
                  AND NOT type(r) IN ['HAS_MENTION', 'INSTANCE_OF']
                RETURN source.name + ' -[:' + type(r) + ']-> ' + coalesce(target.name, target.id) AS rel
                """;
        return persistenceManager.query(
                QuerySpecification.withStatement(cypher)
                        .bind(Map.of("userId", userId))
                        .transform(String.class)
        );
    }

    /**
     * Query entities that were created/modified in this context.
     * Returns entity descriptions as strings to avoid Drivine serialization issues.
     */
    private List<String> queryEntitiesCreatedInContext(String contextId) {
        // Query entities that have propositions in this context
        var cypher = """
                MATCH (p:Proposition)-[:HAS_MENTION]->(m:Mention)
                WHERE p.contextId = $contextId AND m.resolvedId IS NOT NULL
                WITH DISTINCT m.resolvedId AS entityId, m.type AS entityType
                MATCH (e)
                WHERE e.id = entityId
                RETURN '[' + entityType + '] ' + coalesce(e.name, 'unnamed') + ' (id: ' + e.id + ')' AS entity
                """;
        return persistenceManager.query(
                QuerySpecification.withStatement(cypher)
                        .bind(Map.of("contextId", contextId))
                        .transform(String.class)
        );
    }

    @Test
    @DisplayName("Ingest rj.txt with RJ as known user - extraction only")
    void ingestRjFile() throws IOException {
        var testId = "rj_test_" + System.currentTimeMillis();
        var contextId = "rj_context_" + testId;

        System.out.println("=".repeat(70));
        System.out.println("RJ.TXT INGESTION TEST - EXTRACTION ONLY (Transactional - will rollback)");
        System.out.println("=".repeat(70));

        // Create RJ user
        var rjUser = new ImpromptuUser(testId, "RJ", "rj", "rj@test.example.com");
        userService.save(rjUser);

        // Read rj.txt
        var rjText = new ClassPathResource("data/rj.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        System.out.println("Document content:\n" + rjText);

        // Build analysis context
        var knownEntity = KnownEntity.asCurrentUser(rjUser);
        var schema = schemaRegistry.getOrDefault(null);
        var context = SourceAnalysisContext
                .withContextId(contextId)
                .withEntityResolver(entityResolver)
                .withSchema(schema)
                .withRelations(relations)
                .withKnownEntities(knownEntity);

        // Run extraction pipeline only (no persistence)
        System.out.println("\n--- RUNNING PROPOSITION EXTRACTION ---");
        var chunk = Chunk.create(rjText, "rj-preferences-file");
        var result = propositionPipeline.process(List.of(chunk), context);

        // Print and verify results
        var propositions = result.getAllPropositions();
        System.out.println("Extracted " + propositions.size() + " propositions:");
        for (var prop : propositions) {
            System.out.println("  - " + prop.getText());
        }

        // Assertions
        assertFalse(propositions.isEmpty(), "Should extract at least one proposition");
        assertTrue(propositions.stream().anyMatch(p ->
                        p.getText().toLowerCase().contains("wagner") ||
                                p.getText().toLowerCase().contains("music") ||
                                p.getText().toLowerCase().contains("rj")),
                "Should extract propositions related to document content");

        System.out.println("\nTEST COMPLETE - Transaction will rollback");
    }

    @Test
    @DisplayName("Ingest rj.txt with full pipeline including graph projection and Neo4j verification")
    void ingestRjFileWithGraphProjection() throws IOException {
        // Read document - change this to test different documents
        var documentContent = new ClassPathResource("data/rj.txt")
                .getContentAsString(StandardCharsets.UTF_8);

        // Create user context
        var testId = "rj_graph_test_" + System.currentTimeMillis();
        var user = new ImpromptuUser(testId, "RJ", "rj", "rj@test.example.com");

        // Run full ingestion pipeline - all assertions are in ingestDocument()
        var result = ingestDocument(documentContent, "rj-preferences-file", user, "rj_graph");

        // Summary
        System.out.println("\n=== FINAL SUMMARY ===");
        System.out.println("New entities created: " + result.extractionResult().newEntities().size());
        System.out.println("Entities referenced in Neo4j: " + result.persistedEntities().size());
    }

}
