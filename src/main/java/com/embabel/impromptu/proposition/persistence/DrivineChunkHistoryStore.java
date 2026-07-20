/*
 * Copyright 2024-2026 Embabel Pty Ltd.
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

import com.embabel.dice.incremental.AnalysisBookmark;
import com.embabel.dice.incremental.BookmarkKey;
import com.embabel.dice.incremental.ChunkHistoryStore;
import com.embabel.dice.incremental.HashKey;
import com.embabel.dice.incremental.ProcessedChunkRecord;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Neo4j/Drivine implementation of ChunkHistoryStore for tracking processed chunks.
 * <p>
 * Note: keys are matched on content hash / source id only, not the {@code ContextId}
 * scope introduced by the dice key types ({@code ContextId} is a Kotlin value class,
 * so its accessors are not callable from Java). Source ids and content hashes are
 * effectively unique across contexts in this application.
 */
@Service
@Transactional
public class DrivineChunkHistoryStore implements ChunkHistoryStore {

    private static final Logger logger = LoggerFactory.getLogger(DrivineChunkHistoryStore.class);

    private final GraphObjectManager graphObjectManager;
    private final PersistenceManager persistenceManager;

    public DrivineChunkHistoryStore(GraphObjectManager graphObjectManager, PersistenceManager persistenceManager) {
        this.graphObjectManager = graphObjectManager;
        this.persistenceManager = persistenceManager;
    }

    @Override
    @Nullable
    public AnalysisBookmark getLastBookmark(@NonNull BookmarkKey key) {
        var sourceId = key.getSourceId();
        var query = """
                MATCH (c:ProcessedChunk {sourceId: $sourceId})
                RETURN c.sourceId AS sourceId, c.endIndex AS endIndex, c.processedAt AS processedAt
                ORDER BY c.processedAt DESC
                LIMIT 1
                """;

        var spec = QuerySpecification
                .withStatement(query)
                .bind(Map.of("sourceId", sourceId));

        try {
            var result = persistenceManager.getOne(spec);
            if (result == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            var row = (Map<String, Object>) result;
            return new AnalysisBookmark(
                    (String) row.get("sourceId"),
                    ((Number) row.get("endIndex")).intValue(),
                    java.time.Instant.parse(row.get("processedAt").toString())
            );
        } catch (Exception e) {
            logger.debug("No bookmark found for source {}: {}", sourceId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isProcessed(@NonNull HashKey key) {
        var contentHash = key.getContentHash();
        var query = """
                MATCH (c:ProcessedChunk {contentHash: $hash})
                RETURN count(c) > 0 AS exists
                """;

        var spec = QuerySpecification
                .withStatement(query)
                .bind(Map.of("hash", contentHash));

        try {
            var result = persistenceManager.getOne(spec);
            if (result == null) {
                return false;
            }
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.debug("Error checking if processed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void recordProcessed(@NonNull ProcessedChunkRecord record) {
        var node = new ProcessedChunkNode(
                record.getContentHash(),
                record.getSourceId(),
                record.getStartIndex(),
                record.getEndIndex(),
                record.getProcessedAt()
        );

        graphObjectManager.save(node, CascadeType.NONE);
        logger.debug("Recorded processed chunk: {} [{}-{}]",
                record.getSourceId(), record.getStartIndex(), record.getEndIndex());
    }
}
