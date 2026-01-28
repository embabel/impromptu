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
package com.embabel.impromptu.domain;

import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.model.Relationship;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents a musical work in the graph database.
 * Linked to Composer via COMPOSED relationship and to Genre via OF_GENRE relationship.
 */
@JsonClassDescription("A musical work, such as a symphony, concerto, or sonata")
@CreationPermitted(false)
public interface Work extends NamedEntity {
    String getTitle();

    @Nullable String getSubtitle();

    @Nullable String getSearchTerms();

    boolean isPopular();

    boolean isRecommended();

    @Relationship(name = "OF_GENRE")
    Genre getGenre();

    @Relationship(name = "SCORED_FOR")
    List<Instrument> getInstruments();
}
