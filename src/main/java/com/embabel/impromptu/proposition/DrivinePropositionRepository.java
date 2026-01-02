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

import com.embabel.common.core.types.SimilarityResult;
import com.embabel.common.core.types.TextSimilaritySearchRequest;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.impromptu.proposition.graph.PropositionView;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drivine-based proposition repository that persists propositions to Neo4j.
 */
@Service
public class DrivinePropositionRepository implements PropositionRepository {

    private final GraphObjectManager graphObjectManager;

    public DrivinePropositionRepository(GraphObjectManager graphObjectManager) {
        this.graphObjectManager = graphObjectManager;
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
    public @NonNull List<Proposition> findByEntity(@NonNull String entityId) {
        // TODO: Implement with Cypher query filtering by mention resolvedId
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<SimilarityResult<Proposition>> findSimilarWithScores(@NonNull TextSimilaritySearchRequest request) {
        // TODO: Implement with vector similarity search
        return List.of();
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
        // TODO: Implement with Cypher query filtering by grounding array
        return List.of();
    }

    @Override
    @Transactional
    public boolean delete(@NonNull String id) {
        // TODO: Implement deletion
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public int count() {
        return findAll().size();
    }
}