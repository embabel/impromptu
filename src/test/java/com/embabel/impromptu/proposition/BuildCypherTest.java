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
package com.embabel.impromptu.proposition;

import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.impromptu.proposition.persistence.DrivinePropositionRepository;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for DrivinePropositionRepository.buildCypher().
 * These tests don't require a database connection since buildCypher is a pure function.
 */
class BuildCypherTest {

    private DrivinePropositionRepository repository;

    @BeforeEach
    void setUp() {
        // Create repository with mock dependencies - buildCypher doesn't use them
        repository = new DrivinePropositionRepository(
                mock(GraphObjectManager.class),
                mock(PersistenceManager.class),
                mock(EmbeddingService.class)
        );
    }

    /**
     * Create an empty query using factory method.
     * Kotlin data class default constructor isn't available in Java without @JvmOverloads.
     */
    private PropositionQuery emptyQuery() {
        return PropositionQuery.againstContext("__test__");
    }

    @Test
    void buildCypher_contextScopedQuery_generatesContextFilter() {
        var query = PropositionQuery.againstContext("test-context");

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("MATCH (p:Proposition)"));
        assertTrue(result.cypher().contains("RETURN p.id AS id"));
        assertTrue(result.cypher().contains("WHERE p.contextId = $contextId"));
        assertEquals("test-context", result.params().get("contextId"));
    }

    @Test
    void buildCypher_withStatus_addsStatusFilter() {
        var query = emptyQuery().withStatus(PropositionStatus.ACTIVE);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.status = $status"));
        assertEquals("ACTIVE", result.params().get("status"));
    }

    @Test
    void buildCypher_withMinLevel_addsLevelFilter() {
        var query = emptyQuery().withMinLevel(2);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.level >= $minLevel"));
        assertEquals(2, result.params().get("minLevel"));
    }

    @Test
    void buildCypher_withMaxLevel_addsLevelFilter() {
        var query = emptyQuery().withMaxLevel(5);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.level <= $maxLevel"));
        assertEquals(5, result.params().get("maxLevel"));
    }

    @Test
    void buildCypher_withLevelRange_addsBothFilters() {
        var query = emptyQuery()
                .withMinLevel(1)
                .withMaxLevel(3);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.level >= $minLevel"));
        assertTrue(result.cypher().contains("p.level <= $maxLevel"));
        assertEquals(1, result.params().get("minLevel"));
        assertEquals(3, result.params().get("maxLevel"));
    }

    @Test
    void buildCypher_withCreatedAfter_addsTemporalFilter() {
        var instant = Instant.parse("2025-01-01T00:00:00Z");
        var query = emptyQuery().withCreatedAfter(instant);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.createdAt >= $createdAfter"));
        assertEquals(instant.toEpochMilli(), result.params().get("createdAfter"));
    }

    @Test
    void buildCypher_withCreatedBefore_addsTemporalFilter() {
        var instant = Instant.parse("2025-12-31T23:59:59Z");
        var query = emptyQuery().withCreatedBefore(instant);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.createdAt <= $createdBefore"));
        assertEquals(instant.toEpochMilli(), result.params().get("createdBefore"));
    }

    @Test
    void buildCypher_withRevisedAfter_addsTemporalFilter() {
        var instant = Instant.parse("2025-06-01T00:00:00Z");
        var query = emptyQuery().withRevisedAfter(instant);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.revisedAt >= $revisedAfter"));
        assertEquals(instant.toEpochMilli(), result.params().get("revisedAfter"));
    }

    @Test
    void buildCypher_withRevisedBefore_addsTemporalFilter() {
        var instant = Instant.parse("2025-06-30T23:59:59Z");
        var query = emptyQuery().withRevisedBefore(instant);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.revisedAt <= $revisedBefore"));
        assertEquals(instant.toEpochMilli(), result.params().get("revisedBefore"));
    }

    @Test
    void buildCypher_withEntityId_joinsMentions() {
        var query = PropositionQuery.mentioningEntity("entity-123");

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("MATCH (p:Proposition)-[:HAS_MENTION]->(m:Mention)"));
        assertTrue(result.cypher().contains("m.resolvedId = $entityId"));
        assertTrue(result.cypher().contains("RETURN DISTINCT p.id AS id"));
        assertEquals("entity-123", result.params().get("entityId"));
    }

    @Test
    void buildCypher_withEntityIdAndOtherFilters_combinesConditions() {
        var query = PropositionQuery.mentioningEntity("entity-123")
                .withContextIdValue("ctx-1")
                .withStatus(PropositionStatus.ACTIVE);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("m.resolvedId = $entityId"));
        assertTrue(result.cypher().contains("p.contextId = $contextId"));
        assertTrue(result.cypher().contains("p.status = $status"));
        assertEquals("entity-123", result.params().get("entityId"));
        assertEquals("ctx-1", result.params().get("contextId"));
        assertEquals("ACTIVE", result.params().get("status"));
    }

    @Test
    void buildCypher_withOrderByConfidence_addsOrdering() {
        var query = emptyQuery().orderedByEffectiveConfidence();

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("ORDER BY p.confidence DESC"));
    }

    @Test
    void buildCypher_withOrderByCreated_addsOrdering() {
        var query = emptyQuery().orderedByCreated();

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("ORDER BY p.createdAt DESC"));
    }

    @Test
    void buildCypher_withOrderByRevised_addsOrdering() {
        var query = emptyQuery().orderedByRevised();

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("ORDER BY p.revisedAt DESC"));
    }

    @Test
    void buildCypher_withLimit_addsLimit() {
        var query = emptyQuery().withLimit(10);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("LIMIT 10"));
    }

    @Test
    void buildCypher_withMinEffectiveConfidence_addsParams() {
        var asOf = Instant.parse("2025-06-15T12:00:00Z");
        var query = emptyQuery()
                .withMinEffectiveConfidence(0.7)
                .withEffectiveConfidenceAsOf(asOf)
                .withDecayK(1.5);

        var result = repository.buildCypher(query);

        // Confidence filtering is done in post-filter, but params are still set
        assertEquals(0.7, result.params().get("minEffectiveConfidence"));
        assertEquals(asOf.toEpochMilli(), result.params().get("asOfMillis"));
        assertEquals(1.5, result.params().get("decayK"));
    }

    @Test
    void buildCypher_complexQuery_combinesAllConditions() {
        var createdAfter = Instant.parse("2025-01-01T00:00:00Z");
        var query = PropositionQuery.againstContext("session-42")
                .withStatus(PropositionStatus.ACTIVE)
                .withMinLevel(1)
                .withMaxLevel(5)
                .withCreatedAfter(createdAfter)
                .orderedByEffectiveConfidence()
                .withLimit(20);

        var result = repository.buildCypher(query);

        assertTrue(result.cypher().contains("p.contextId = $contextId"));
        assertTrue(result.cypher().contains("p.status = $status"));
        assertTrue(result.cypher().contains("p.level >= $minLevel"));
        assertTrue(result.cypher().contains("p.level <= $maxLevel"));
        assertTrue(result.cypher().contains("p.createdAt >= $createdAfter"));
        assertTrue(result.cypher().contains("ORDER BY p.confidence DESC"));
        assertTrue(result.cypher().contains("LIMIT 20"));

        assertEquals("session-42", result.params().get("contextId"));
        assertEquals("ACTIVE", result.params().get("status"));
        assertEquals(1, result.params().get("minLevel"));
        assertEquals(5, result.params().get("maxLevel"));
        assertEquals(createdAfter.toEpochMilli(), result.params().get("createdAfter"));
    }
}
