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
package com.embabel.impromptu.proposition;

import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.MentionRole;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.impromptu.TestDrivineConfiguration;
import com.embabel.impromptu.proposition.persistence.DrivinePropositionRepository;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestDrivineConfiguration.class)
@ActiveProfiles("testcontainer")
@Transactional
class DrivinePropositionRepositoryTest {

    private static final String TEST_CONTEXT = "test-context";
    private static final float[] TEST_EMBEDDING = new float[]{0.1f, 0.2f, 0.3f};

    @Autowired
    private GraphObjectManager graphObjectManager;

    @Autowired
    private PersistenceManager persistenceManager;

    private DrivinePropositionRepository repository;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        // Create mock embedding service
        embeddingService = Mockito.mock(EmbeddingService.class);
        when(embeddingService.embed(anyString())).thenReturn(TEST_EMBEDDING);

        repository = new DrivinePropositionRepository(graphObjectManager,
                persistenceManager, embeddingService);
        // Clean up any existing test propositions
        persistenceManager.execute(
                QuerySpecification.withStatement("MATCH (p:Proposition) DETACH DELETE p")
        );
        persistenceManager.execute(
                QuerySpecification.withStatement("MATCH (m:Mention) DETACH DELETE m")
        );
    }

    private Proposition createProposition(String id, String text, List<EntityMention> mentions,
                                          double confidence, PropositionStatus status) {
        return Proposition.create(
                id,
                TEST_CONTEXT,
                text,
                mentions,
                confidence,
                0.0,  // decay
                null, // reasoning
                List.of(), // grounding
                Instant.now(),
                Instant.now(),
                status,
                0,    // level
                List.of(), // sourceIds
                Map.of(),  // metadata
                null  // uri
        );
    }

    @Test
    void saveAndFindById() {
        // Given
        var mention1 = new EntityMention("Jim", "Person", null, MentionRole.SUBJECT, Map.of());
        var mention2 = new EntityMention("Neo4j", "Technology", null, MentionRole.OBJECT, Map.of());
        var proposition = createProposition(
                "test-prop-1",
                "Jim is an expert in Neo4j",
                List.of(mention1, mention2),
                0.9,
                PropositionStatus.ACTIVE
        );

        // When
        repository.save(proposition);
        var found = repository.findById("test-prop-1");

        // Then
        assertNotNull(found);
        assertEquals("test-prop-1", found.getId());
        assertEquals("Jim is an expert in Neo4j", found.getText());
        assertEquals(0.9, found.getConfidence(), 0.001);
        assertEquals(PropositionStatus.ACTIVE, found.getStatus());
        assertEquals(2, found.getMentions().size());
    }

    @Test
    void findByIdReturnsNullWhenNotFound() {
        // When
        var found = repository.findById("non-existent");

        // Then
        assertNull(found);
    }

    @Test
    void findAll() {
        // Given
        var prop1 = createProposition("test-prop-all-1", "First proposition", List.of(), 0.8, PropositionStatus.ACTIVE);
        var prop2 = createProposition("test-prop-all-2", "Second proposition", List.of(), 0.7, PropositionStatus.ACTIVE);

        repository.save(prop1);
        repository.save(prop2);

        // When
        var all = repository.findAll();

        // Then
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.getId().equals("test-prop-all-1")));
        assertTrue(all.stream().anyMatch(p -> p.getId().equals("test-prop-all-2")));
    }

    @Test
    void findByStatus() {
        // Given
        var activeProp = createProposition("test-active", "Active proposition", List.of(), 0.8, PropositionStatus.ACTIVE);
        var supersededProp = createProposition("test-superseded", "Superseded proposition", List.of(), 0.6, PropositionStatus.SUPERSEDED);

        repository.save(activeProp);
        repository.save(supersededProp);

        // When
        var activeResults = repository.findByStatus(PropositionStatus.ACTIVE);
        var supersededResults = repository.findByStatus(PropositionStatus.SUPERSEDED);

        // Then
        assertEquals(1, activeResults.size());
        assertEquals("test-active", activeResults.getFirst().getId());

        assertEquals(1, supersededResults.size());
        assertEquals("test-superseded", supersededResults.getFirst().getId());
    }

    @Test
    void count() {
        // Given
        assertEquals(0, repository.count());

        var prop = createProposition("test-count", "Test proposition", List.of(), 0.8, PropositionStatus.ACTIVE);
        repository.save(prop);

        // When/Then
        assertEquals(1, repository.count());
    }
}
