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

import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;

import java.time.Instant;

/**
 * Neo4j node representing a processed chunk for incremental analysis tracking.
 */
@NodeFragment(labels = {"ProcessedChunk"})
public class ProcessedChunkNode {

    @NodeId
    private String id;

    private String contentHash;
    private String sourceId;
    private int startIndex;
    private int endIndex;
    private Instant processedAt;

    public ProcessedChunkNode() {
    }

    public ProcessedChunkNode(String contentHash, String sourceId, int startIndex, int endIndex, Instant processedAt) {
        this.id = contentHash; // Use hash as ID for dedup
        this.contentHash = contentHash;
        this.sourceId = sourceId;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
