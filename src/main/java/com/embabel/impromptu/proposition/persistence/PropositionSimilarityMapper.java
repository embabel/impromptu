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

import org.drivine.mapper.RowMapper;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Maps Neo4j vector search results to proposition ID and score pairs.
 * Uses Drivine's RowMapper interface which properly handles Neo4j driver value types.
 */
class PropositionSimilarityMapper implements RowMapper<PropositionSimilarityResult> {

    @Override
    public @NonNull PropositionSimilarityResult map(@NonNull Map<String, ?> row) {
        var id = (String) row.get("id");
        var score = ((Number) row.get("score")).doubleValue();
        return new PropositionSimilarityResult(id, score);
    }
}
