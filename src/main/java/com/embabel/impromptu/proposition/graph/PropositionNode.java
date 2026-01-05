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
package com.embabel.impromptu.proposition.graph;

import com.embabel.dice.proposition.PropositionStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A proposition is a natural language statement with typed entity mentions.
 * Neo4j graph representation of Proposition from the Dice project.
 * <p>
 * Propositions are the system of record - all other representations
 * (Neo4j relationships, vector embeddings) derive from them.
 */
@NodeFragment(labels = {"Proposition"})
public class PropositionNode {

    @NodeId
    private String id;

    /** The context in which this proposition is relevant */
    private String contextId;

    /** The statement in natural language (e.g., "Jim is an expert in GOAP") */
    private String text;

    /** LLM-generated certainty (0.0-1.0) */
    private double confidence;

    /** Staleness rate (0.0-1.0). High decay = information becomes stale quickly */
    private double decay;

    /** LLM explanation for why this was extracted */
    private @Nullable String reasoning;

    /** Chunk IDs that support this proposition */
    private List<String> grounding;

    /** When the proposition was first created */
    private Instant created;

    /** When the proposition was last updated */
    private Instant revised;

    /** Current lifecycle status */
    private PropositionStatus status;

    /** Optional URI reference */
    private @Nullable String uri;

    /** Vector embedding for similarity search */
    private @Nullable List<Double> embedding;

    @JsonCreator
    public PropositionNode(
            @JsonProperty("id") String id,
            @JsonProperty("contextId") String contextId,
            @JsonProperty("text") String text,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("decay") double decay,
            @JsonProperty("reasoning") @Nullable String reasoning,
            @JsonProperty("grounding") List<String> grounding,
            @JsonProperty("created") Instant created,
            @JsonProperty("revised") Instant revised,
            @JsonProperty("status") PropositionStatus status,
            @JsonProperty("uri") @Nullable String uri) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.contextId = contextId != null ? contextId : "default";
        this.text = text;
        this.confidence = confidence;
        this.decay = decay;
        this.reasoning = reasoning;
        this.grounding = grounding != null ? grounding : List.of();
        this.created = created != null ? created : Instant.now();
        this.revised = revised != null ? revised : Instant.now();
        this.status = status != null ? status : PropositionStatus.ACTIVE;
        this.uri = uri;
    }

    public PropositionNode(String text, double confidence) {
        this(UUID.randomUUID().toString(), "default", text, confidence, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null);
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getDecay() {
        return decay;
    }

    public void setDecay(double decay) {
        this.decay = decay;
    }

    public @Nullable String getReasoning() {
        return reasoning;
    }

    public void setReasoning(@Nullable String reasoning) {
        this.reasoning = reasoning;
    }

    public List<String> getGrounding() {
        return grounding;
    }

    public void setGrounding(List<String> grounding) {
        this.grounding = grounding;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getRevised() {
        return revised;
    }

    public void setRevised(Instant revised) {
        this.revised = revised;
    }

    public PropositionStatus getStatus() {
        return status;
    }

    public void setStatus(PropositionStatus status) {
        this.status = status;
    }

    public @Nullable String getUri() {
        return uri;
    }

    public void setUri(@Nullable String uri) {
        this.uri = uri;
    }

    public @Nullable List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(@Nullable List<Double> embedding) {
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "PropositionNode{" +
                "id='" + id + '\'' +
                ", text='" + text + '\'' +
                ", confidence=" + confidence +
                ", status=" + status +
                '}';
    }
}