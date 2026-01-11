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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents a composer in the graph database.
 * Linked to Epoch via OF_EPOCH relationship and to Works via COMPOSED relationship.
 */
@CreationPermitted(false)
public interface Composer extends NamedEntity {
    String getCompleteName();

    @Nullable Long getBirthYear();

    @Nullable Long getDeathYear();

    @Relationship(name = "COMPOSED")
    List<Work> getWorks();

    default Long lifespan() {
        Long birthYear = getBirthYear();
        Long deathYear = getDeathYear();
        if (birthYear != null && deathYear != null) {
            return deathYear - birthYear;
        }
        return null;
    }

    boolean isPopular();

    boolean isRecommended();
}
