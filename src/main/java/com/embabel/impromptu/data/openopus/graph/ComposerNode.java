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
package com.embabel.impromptu.data.openopus.graph;

import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a composer in the graph database.
 * Linked to Epoch via OF_EPOCH relationship and to Works via COMPOSED relationship.
 */
@NodeFragment(labels = {"Entity", "Composer"})
@CreationPermitted(false)
public record ComposerNode(
        @NodeId String id,
        String name,
        String completeName,
        @Nullable String birth,
        @Nullable String death,
        boolean popular,
        boolean recommended
) implements NamedEntity {

    @Override
    public @NonNull String getId() {
        return id;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    @Override
    public @NonNull String getDescription() {
        return "Composer: " + completeName;
    }
}
