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

import com.embabel.agent.rag.service.EntityIdentifier;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.common.core.types.SimpleSimilaritySearchResult;
import com.embabel.common.core.types.TextSimilaritySearchRequest;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.impromptu.proposition.graph.PropositionView;
import jakarta.annotation.PostConstruct;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Drivine-based proposition repository that persists propositions to Neo4j.
 */
@Service
public class DrivinePropositionRepository implements PropositionRepository {

    private static final Logger logger = LoggerFactory.getLogger(DrivinePropositionRepository.class);
    private static final String PROPOSITION_VECTOR_INDEX = "proposition_embedding_index";

    private final GraphObjectManager graphObjectManager;
    private final PersistenceManager persistenceManager;
    private final EmbeddingService embeddingService;

    public DrivinePropositionRepository(
            GraphObjectManager graphObjectManager,
            PersistenceManager persistenceManager,
            EmbeddingService embeddingService) {
        this.graphObjectManager = graphObjectManager;
        this.persistenceManager = persistenceManager;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void provision() {
        logger.info("Provisioning proposition vector index");
        createVectorIndex(PROPOSITION_VECTOR_INDEX, "Proposition");
    }

    private void createVectorIndex(String name, String label) {
        var statement = """
                CREATE VECTOR INDEX `%s` IF NOT EXISTS
                FOR (n:%s) ON (n.embedding)
                OPTIONS {indexConfig: {
                    `vector.dimensions`: %d,
                    `vector.similarity_function`: 'cosine'
                }}
                """.formatted(name, label, ((EmbeddingModel) embeddingService.getModel()).dimensions());
        try {
            persistenceManager.execute(QuerySpecification.withStatement(statement));
            logger.info("Created vector index {} on {}", name, label);
        } catch (Exception e) {
            logger.warn("Could not create vector index {}: {}", name, e.getMessage());
        }
    }

    @Override
    public @NonNull String getLuceneSyntaxNotes() {
        return "fully supported";
    }

    @Override
    @Transactional
    public @NonNull Proposition save(@NonNull Proposition proposition) {
        var view = PropositionView.fromDice(proposition);
        graphObjectManager.save(view, CascadeType.NONE);

        // Set embedding using raw Cypher (like embabel-agent-rag-neo-drivine does)
        var embedding = embeddingService.embed(proposition.getText());
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.embedding = $embedding
                RETURN count(p) AS updated
                """;
        var params = Map.of(
                "id", proposition.getId(),
                "embedding", embedding
        );
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Set embedding for proposition {}", proposition.getId());
        } catch (Exception e) {
            logger.warn("Failed to set embedding for proposition {}: {}", proposition.getId(), e.getMessage());
        }
        return proposition;
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable Proposition findById(@NonNull String id) {
        var view = graphObjectManager.load(id, PropositionView.class);
        return view != null ? view.toDice() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findAll() {
        return graphObjectManager.loadAll(PropositionView.class).stream()
                .map(PropositionView::toDice)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByEntity(@NonNull EntityIdentifier identifier) {
        // TODO make this efficient with query
        return findAll().stream().filter(p ->
                p.getMentions().stream().anyMatch(m ->
                        m.getType().equalsIgnoreCase(identifier.getType()) &&
                                identifier.getId().equals(m.getResolvedId())
                )
        ).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<SimilarityResult<Proposition>> findSimilarWithScores(@NonNull TextSimilaritySearchRequest request) {
        var embedding = embeddingService.embed(request.getQuery());
        var cypher = """
                CALL db.index.vector.queryNodes($vectorIndex, $topK, $queryVector)
                YIELD node AS p, score
                WHERE score >= $similarityThreshold
                RETURN {
                    id: p.id,
                    score: score
                } AS result
                ORDER BY score DESC
                """;

        var params = Map.of(
                "vectorIndex", PROPOSITION_VECTOR_INDEX,
                "topK", request.getTopK(),
                "queryVector", embedding,
                "similarityThreshold", request.getSimilarityThreshold()
        );

        logger.debug("Executing proposition vector search with query: {}", request.getQuery());

        try {
            var rows = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .mapWith(new PropositionSimilarityMapper())
            );

            logger.debug("Vector search returned {} rows", rows.size());

            return rows.stream()
                    .<SimilarityResult<Proposition>>map(row -> {
                        var proposition = findById(row.id());
                        return proposition != null
                                ? new SimpleSimilaritySearchResult<>(proposition, row.score())
                                : null;
                    })
                    .filter(r -> r != null)
                    .toList();
        } catch (Exception e) {
            logger.error("Vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByStatus(@NonNull PropositionStatus status) {
        var whereClause = "proposition.status = '" + status.name() + "'";
        return graphObjectManager.loadAll(PropositionView.class, whereClause).stream()
                .map(PropositionView::toDice)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByGrounding(@NonNull String chunkId) {
        // TODO make this efficient with query
        return findAll().stream()
                .filter(p -> p.getGrounding().contains(chunkId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByContextIdValue(@NonNull String contextIdValue) {
        var whereClause = "proposition.contextId = '" + contextIdValue + "'";
        return graphObjectManager.loadAll(PropositionView.class, whereClause).stream()
                .map(PropositionView::toDice)
                .toList();
    }

    @Override
    @Transactional
    public boolean delete(@NonNull String id) {
        int deleted = graphObjectManager.delete(id, PropositionView.class);
        return deleted > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public int count() {
        var spec = QuerySpecification
                .withStatement("MATCH (p:Proposition) RETURN count(p) AS count")
                .transform(Long.class);
        Long result = persistenceManager.getOne(spec);
        return result.intValue();
    }
}