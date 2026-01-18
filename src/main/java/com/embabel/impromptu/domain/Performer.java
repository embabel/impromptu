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
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

/**
 * Represents a performer (pianist, conductor, orchestra, singer, etc.) in the graph database.
 * Distinct from Composer - a performer interprets and performs works.
 * Note: Some individuals may be both composers and performers (e.g., Rachmaninoff, Liszt).
 */
@CreationPermitted(true)
@JsonClassDescription("A performer such as a pianist, conductor, orchestra, or singer." +
        "User the performerType property to specify the type of performer.")
public interface Performer extends NamedEntity {

    /**
     * The type of performer: pianist, conductor, violinist, orchestra, soprano, etc.
     */
    @JsonPropertyDescription("The type of performer: pianist, conductor, violinist, orchestra, singer, etc. Use lower case")
    @Nullable String getPerformerType();
}
