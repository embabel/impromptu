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
package com.embabel.impromptu.proposition.persistence;

import com.embabel.agent.rag.service.RetrievableIdentifier;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.common.core.types.SimpleSimilaritySearchResult;
import com.embabel.common.core.types.TextSimilaritySearchRequest;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
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
    public @NonNull List<Proposition> findByMinLevel(int minLevel) {
        var whereClause = "proposition.level >= " + minLevel;
        return graphObjectManager.loadAll(PropositionView.class, whereClause).stream()
                .map(PropositionView::toDice)
                .toList();
    }

    /**
     * Find propositions with minimum level for a specific context.
     * This is more efficient than findByMinLevel() when context filtering is needed.
     */
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByMinLevelAndContext(int minLevel, @NonNull String contextId) {
        var whereClause = "proposition.level >= " + minLevel + " AND proposition.contextId = '" + contextId + "'";
        return graphObjectManager.loadAll(PropositionView.class, whereClause).stream()
                .map(PropositionView::toDice)
                .toList();
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
    public @NonNull List<Proposition> findByEntity(@NonNull RetrievableIdentifier identifier) {
        // Use Cypher query with traversal for efficiency
        var cypher = """
                MATCH (p:Proposition)-[:HAS_MENTION]->(m:Mention)
                WHERE m.resolvedId = $resolvedId
                  AND (toLower(m.type) = toLower($type)
                       OR (toLower($type) = 'user' AND toLower(m.type) CONTAINS 'user'))
                RETURN DISTINCT p.id AS id
                """;
        var params = Map.of(
                "resolvedId", identifier.getId(),
                "type", identifier.getType()
        );

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                    .map(this::findById)
                    .filter(p -> p != null)
                    .toList();
        } catch (Exception e) {
            logger.warn("findByEntity query failed: {}, falling back to in-memory", e.getMessage());
            // Fallback to in-memory filtering
            return findAll().stream().filter(p ->
                    p.getMentions().stream().anyMatch(m ->
                            isTypeCompatible(m.getType(), identifier.getType()) &&
                                    identifier.getId().equals(m.getResolvedId())
                    )
            ).toList();
        }
    }

    /**
     * Check if a mention type is compatible with an identifier type.
     * Handles cases where the stored type is a class name (e.g., "ImpromptuUser")
     * but the identifier uses a label (e.g., "User").
     */
    private boolean isTypeCompatible(String mentionType, String identifierType) {
        if (mentionType.equalsIgnoreCase(identifierType)) {
            return true;
        }
        // Handle User variants - stored as "ImpromptuUser" but queried as "User"
        if ("User".equalsIgnoreCase(identifierType)) {
            return mentionType.toLowerCase().contains("user");
        }
        return false;
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

    /**
     * Result of building a Cypher query from a PropositionQuery.
     * Exposed for testing.
     */
    public record CypherQuery(String cypher, Map<String, Object> params) {}

    /**
     * Build a Cypher query from a PropositionQuery.
     * This method is exposed for testing purposes.
     *
     * @param query the proposition query
     * @return the Cypher query string and parameters
     */
    public CypherQuery buildCypher(@NonNull PropositionQuery query) {
        var whereConditions = new java.util.ArrayList<String>();
        var params = new java.util.HashMap<String, Object>();

        // Context filter
        if (query.getContextIdValue() != null) {
            whereConditions.add("p.contextId = $contextId");
            params.put("contextId", query.getContextIdValue());
        }

        // Status filter
        if (query.getStatus() != null) {
            whereConditions.add("p.status = $status");
            params.put("status", query.getStatus().name());
        }

        // Level filters
        if (query.getMinLevel() != null) {
            whereConditions.add("p.level >= $minLevel");
            params.put("minLevel", query.getMinLevel());
        }
        if (query.getMaxLevel() != null) {
            whereConditions.add("p.level <= $maxLevel");
            params.put("maxLevel", query.getMaxLevel());
        }

        // Temporal filters - created
        if (query.getCreatedAfter() != null) {
            whereConditions.add("p.createdAt >= $createdAfter");
            params.put("createdAfter", query.getCreatedAfter().toEpochMilli());
        }
        if (query.getCreatedBefore() != null) {
            whereConditions.add("p.createdAt <= $createdBefore");
            params.put("createdBefore", query.getCreatedBefore().toEpochMilli());
        }

        // Temporal filters - revised
        if (query.getRevisedAfter() != null) {
            whereConditions.add("p.revisedAt >= $revisedAfter");
            params.put("revisedAfter", query.getRevisedAfter().toEpochMilli());
        }
        if (query.getRevisedBefore() != null) {
            whereConditions.add("p.revisedAt <= $revisedBefore");
            params.put("revisedBefore", query.getRevisedBefore().toEpochMilli());
        }

        // Effective confidence params (decay calculation done in post-filter)
        if (query.getMinEffectiveConfidence() != null) {
            var asOf = query.getEffectiveConfidenceAsOf() != null
                    ? query.getEffectiveConfidenceAsOf()
                    : java.time.Instant.now();
            params.put("minEffectiveConfidence", query.getMinEffectiveConfidence());
            params.put("asOfMillis", asOf.toEpochMilli());
            params.put("decayK", query.getDecayK());
        }

        // Build base query - handle entity filter specially (requires join)
        String cypher;
        if (query.getEntityId() != null) {
            // Join with mentions to filter by entity
            params.put("entityId", query.getEntityId());
            var additionalConditions = whereConditions.isEmpty() ? "" : " AND " + String.join(" AND ", whereConditions);
            cypher = """
                    MATCH (p:Proposition)-[:HAS_MENTION]->(m:Mention)
                    WHERE m.resolvedId = $entityId%s
                    RETURN DISTINCT p.id AS id
                    """.formatted(additionalConditions);
        } else {
            var whereClause = whereConditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", whereConditions);
            cypher = """
                    MATCH (p:Proposition)
                    %s
                    RETURN p.id AS id
                    """.formatted(whereClause);
        }

        // Add ordering
        cypher = switch (query.getOrderBy()) {
            case EFFECTIVE_CONFIDENCE_DESC -> cypher.replace("RETURN", "ORDER BY p.confidence DESC RETURN");
            case CREATED_DESC -> cypher.replace("RETURN", "ORDER BY p.createdAt DESC RETURN");
            case REVISED_DESC -> cypher.replace("RETURN", "ORDER BY p.revisedAt DESC RETURN");
            case NONE -> cypher;
        };

        // Add limit
        if (query.getLimit() != null) {
            cypher += " LIMIT " + query.getLimit();
        }

        return new CypherQuery(cypher, params);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> query(@NonNull PropositionQuery query) {
        var cypherQuery = buildCypher(query);
        logger.debug("Executing proposition query: {}", cypherQuery.cypher());

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypherQuery.cypher())
                            .bind(cypherQuery.params())
                            .transform(String.class)
            );

            var results = ids.stream()
                    .map(this::findById)
                    .filter(p -> p != null)
                    .toList();

            // Post-filter for effective confidence if needed (decay calculation)
            if (query.getMinEffectiveConfidence() != null) {
                var asOf = query.getEffectiveConfidenceAsOf() != null
                        ? query.getEffectiveConfidenceAsOf()
                        : java.time.Instant.now();
                var k = query.getDecayK();
                var threshold = query.getMinEffectiveConfidence();

                results = results.stream()
                        .filter(p -> {
                            var daysSinceRevision = java.time.Duration.between(
                                    p.getRevised() != null ? p.getRevised() : p.getCreated(),
                                    asOf
                            ).toDays();
                            var effectiveConfidence = p.getConfidence() * Math.exp(-k * daysSinceRevision / 365.0);
                            return effectiveConfidence >= threshold;
                        })
                        .toList();
            }

            return results;
        } catch (Exception e) {
            logger.error("Proposition query failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<SimilarityResult<Proposition>> findSimilarWithScores(
            @NonNull TextSimilaritySearchRequest request,
            @NonNull PropositionQuery query) {
        var embedding = embeddingService.embed(request.getQuery());

        // Build WHERE clause from query filters
        var whereConditions = new java.util.ArrayList<String>();
        whereConditions.add("score >= $similarityThreshold");

        var params = new java.util.HashMap<String, Object>();
        params.put("vectorIndex", PROPOSITION_VECTOR_INDEX);
        params.put("topK", request.getTopK());
        params.put("queryVector", embedding);
        params.put("similarityThreshold", request.getSimilarityThreshold());

        if (query.getContextIdValue() != null) {
            whereConditions.add("p.contextId = $contextId");
            params.put("contextId", query.getContextIdValue());
        }
        if (query.getStatus() != null) {
            whereConditions.add("p.status = $status");
            params.put("status", query.getStatus().name());
        }
        if (query.getMinLevel() != null) {
            whereConditions.add("p.level >= $minLevel");
            params.put("minLevel", query.getMinLevel());
        }
        if (query.getMaxLevel() != null) {
            whereConditions.add("p.level <= $maxLevel");
            params.put("maxLevel", query.getMaxLevel());
        }

        var whereClause = String.join(" AND ", whereConditions);
        var cypher = """
                CALL db.index.vector.queryNodes($vectorIndex, $topK, $queryVector)
                YIELD node AS p, score
                WHERE %s
                RETURN {
                    id: p.id,
                    score: score
                } AS result
                ORDER BY score DESC
                """.formatted(whereClause);

        logger.debug("Executing filtered proposition vector search with query: {}, contextId: {}",
                request.getQuery(), query.getContextIdValue());

        try {
            var rows = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .mapWith(new PropositionSimilarityMapper())
            );

            logger.debug("Filtered vector search returned {} rows", rows.size());

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
            logger.error("Filtered vector search failed: {}", e.getMessage(), e);
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
        // Use Cypher query for efficiency - grounding is stored as an array property
        var cypher = """
                MATCH (p:Proposition)
                WHERE $chunkId IN p.grounding
                RETURN p.id AS id
                """;
        var params = Map.of("chunkId", chunkId);

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                    .map(this::findById)
                    .filter(p -> p != null)
                    .toList();
        } catch (Exception e) {
            logger.warn("findByGrounding query failed: {}, falling back to in-memory", e.getMessage());
            return findAll().stream()
                    .filter(p -> p.getGrounding().contains(chunkId))
                    .toList();
        }
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

    /**
     * Delete all propositions from the database.
     *
     * @return the number of propositions deleted
     */
    @Transactional
    public int clearAll() {
        var countSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition) RETURN count(p) AS count")
                .transform(Long.class);
        Long count = persistenceManager.getOne(countSpec);

        var deleteSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition) DETACH DELETE p");
        persistenceManager.execute(deleteSpec);

        logger.info("Deleted {} propositions", count);
        return count.intValue();
    }

    /**
     * Delete all propositions for a specific context.
     *
     * @param contextId the context ID to clear propositions for
     * @return the number of propositions deleted
     */
    @Transactional
    public int clearByContext(@NonNull String contextId) {
        var countSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition {contextId: $contextId}) RETURN count(p) AS count")
                .bind(Map.of("contextId", contextId))
                .transform(Long.class);
        Long count = persistenceManager.getOne(countSpec);

        var deleteSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition {contextId: $contextId}) DETACH DELETE p")
                .bind(Map.of("contextId", contextId));
        persistenceManager.execute(deleteSpec);

        logger.info("Deleted {} propositions for context {}", count, contextId);
        return count.intValue();
    }
}