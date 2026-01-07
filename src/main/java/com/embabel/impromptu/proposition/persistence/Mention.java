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

import com.embabel.dice.proposition.EntityMention;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A reference to an entity within a proposition.
 * Neo4j graph representation of EntityMention from the Dice project.
 */
@NodeFragment(labels = {"Mention"})
public class Mention {

    @NodeId
    private String id;

    /**
     * The text as it appears in the proposition (e.g., "Jim")
     */
    private String span;

    /**
     * The entity type label from schema (e.g., "Person", "Technology")
     */
    private String type;

    /**
     * Entity ID if resolved, null if unresolved
     */
    private @Nullable String resolvedId;

    /**
     * The role this entity plays in the proposition
     */
    private MentionRole role;

    @JsonCreator
    public Mention(
            @JsonProperty("id") String id,
            @JsonProperty("span") String span,
            @JsonProperty("type") String type,
            @JsonProperty("resolvedId") @Nullable String resolvedId,
            @JsonProperty("role") MentionRole role) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.span = span;
        this.type = type;
        this.resolvedId = resolvedId;
        this.role = role != null ? role : MentionRole.OTHER;
    }

    public Mention(String span, String type, @Nullable String resolvedId, MentionRole role) {
        this(UUID.randomUUID().toString(), span, type, resolvedId, role);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSpan() {
        return span;
    }

    public void setSpan(String span) {
        this.span = span;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public @Nullable String getResolvedId() {
        return resolvedId;
    }

    public void setResolvedId(@Nullable String resolvedId) {
        this.resolvedId = resolvedId;
    }

    public MentionRole getRole() {
        return role;
    }

    public void setRole(MentionRole role) {
        this.role = role;
    }

    /**
     * Create a Mention from a Dice EntityMention.
     */
    public static Mention fromDice(EntityMention em) {
        MentionRole role = switch (em.getRole()) {
            case SUBJECT -> MentionRole.SUBJECT;
            case OBJECT -> MentionRole.OBJECT;
            case OTHER -> MentionRole.OTHER;
        };
        return new Mention(em.getSpan(), em.getType(), em.getResolvedId(), role);
    }

    /**
     * Convert this Mention back to a Dice EntityMention.
     */
    public EntityMention toDice() {
        com.embabel.dice.proposition.MentionRole diceRole = switch (role) {
            case SUBJECT -> com.embabel.dice.proposition.MentionRole.SUBJECT;
            case OBJECT -> com.embabel.dice.proposition.MentionRole.OBJECT;
            case OTHER -> com.embabel.dice.proposition.MentionRole.OTHER;
        };
        return new EntityMention(span, type, resolvedId, diceRole, java.util.Map.of());
    }

    @Override
    public String toString() {
        String resolved = resolvedId != null ? "→" + resolvedId : "?";
        return span + ":" + type + resolved;
    }
}