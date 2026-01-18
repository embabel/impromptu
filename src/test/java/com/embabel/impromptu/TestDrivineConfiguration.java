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
package com.embabel.impromptu;

import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivineTestConfig;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.GraphObjectManagerFactory;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for Drivine with smart Testcontainers integration.
 * <p>
 * This configuration provides:
 * - Local Development: Use your local Neo4j instance when USE_LOCAL_NEO4J=true
 * - CI/Automated Tests: Automatically use Testcontainers when USE_LOCAL_NEO4J=false (default)
 * <p>
 * To use local Neo4j instead of Testcontainers, set:
 * - Environment variable: USE_LOCAL_NEO4J=true
 * - Or system property: -Dtest.neo4j.use-local=true
 */
@Configuration
@Profile("testcontainer")
@EnableDrivine
@EnableDrivineTestConfig
public class TestDrivineConfiguration {

    @Bean
    @Primary
    public PersistenceManager persistenceManager(PersistenceManagerFactory factory) {
        return factory.get("neo");
    }

    @Bean
    @Primary
    public GraphObjectManager graphObjectManager(GraphObjectManagerFactory factory) {
        return factory.get("neo");
    }
}