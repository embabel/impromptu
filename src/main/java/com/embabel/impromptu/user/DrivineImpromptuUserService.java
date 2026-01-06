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
package com.embabel.impromptu.user;

import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drivine-based user service that persists users to Neo4j.
 */
public class DrivineImpromptuUserService extends ImpromptuUserService {

    private final GraphObjectManager graphObjectManager;

    public DrivineImpromptuUserService(GraphObjectManager graphObjectManager) {
        this.graphObjectManager = graphObjectManager;
    }

    @Override
    @Transactional
    protected ImpromptuUser findOrCreate(String id, String displayName, String username, String email) {
        ImpromptuUser existing = findById(id);
        if (existing != null) {
            logger.info("Found existing user by ID: {}", existing);
            return existing;
        }

        // Create and save new user
        var newUser = new ImpromptuUser(id, displayName, username, email);
        save(newUser);
        logger.info("Provisioned new user: {}", newUser);
        return newUser;
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable ImpromptuUser findById(@NonNull String id) {
        return graphObjectManager.load(id, ImpromptuUser.class);
    }

    @Override
    @Transactional
    public ImpromptuUser save(ImpromptuUser user) {
        return graphObjectManager.save(user, CascadeType.NONE);
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable ImpromptuUser findByUsername(@NonNull String username) {
        var results = graphObjectManager.loadAll(ImpromptuUser.class, "n.username = '" + username + "'");
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable ImpromptuUser findByEmail(@NonNull String email) {
        var results = graphObjectManager.loadAll(ImpromptuUser.class, "n.email = '" + email + "'");
        return results.isEmpty() ? null : results.getFirst();
    }
}