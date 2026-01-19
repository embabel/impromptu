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

import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.user.ImpromptuUserService;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivineTestConfig;
import org.drivine.manager.PersistenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the Spring application context loads successfully
 * with all core beans properly configured.
 * <p>
 * This test requires LLM API keys to be configured and is excluded from normal CI builds.
 * Run manually with: mvn test -Dtest="*IT"
 * <p>
 * Uses Testcontainers for Neo4j (via @EnableDrivineTestConfig).
 */
@SpringBootTest(
        properties = {
                // Allow bean overrides for test configuration
                "spring.main.allow-bean-definition-overriding=true",
                // Disable MCP client
                "spring.ai.mcp.client.enabled=false",
                // Provide test values for required properties
                "impromptu.objective=test",
                "impromptu.behaviour=test",
                "impromptu.embedding-service=text-embedding-3-small",
        }
)
@EnableDrivine
@EnableDrivineTestConfig
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
class ImpromptuApplicationSmokeIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ImpromptuUserService userService;

    @Autowired
    private DrivineStore drivineStore;

    @Autowired
    private PropositionRepository propositionRepository;

    @Autowired
    private PropositionPipeline propositionPipeline;

    @Autowired
    private PersistenceManager persistenceManager;

    private TestDataHelper testDataHelper;

    @BeforeEach
    void setUp() {
        testDataHelper = new TestDataHelper(persistenceManager);
        testDataHelper.clearTestData();
        testDataHelper.createAllTestData();
    }

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Application context should load");
    }

    @Test
    void coreBeansShouldBeConfigured() {
        assertNotNull(userService, "ImpromptuUserService should be configured");
        assertNotNull(drivineStore, "DrivineStore should be configured");
        assertNotNull(propositionRepository, "PropositionRepository should be configured");
        assertNotNull(propositionPipeline, "PropositionPipeline should be configured");
    }

    @Test
    void drivineStoreShouldBeProvisioned() {
        var info = drivineStore.info();
        assertNotNull(info, "DrivineStore info should be available");
        // Store should be provisioned (indexes created)
        // Chunk count will be 0 in fresh test database
    }

    @Test
    void testDataShouldCreateComposers() {
        var count = testDataHelper.countComposers();
        assertEquals(5, count, "Should have 5 test composers");
    }

    @Test
    void testDataShouldCreateWorks() {
        var count = testDataHelper.countWorks();
        assertEquals(9, count, "Should have 9 test works");
    }

    @Test
    void composersAndWorksShouldBeLinked() {
        // Verify the COMPOSED relationship exists
        var beethovenWorks = persistenceManager.getOne(
                org.drivine.query.QuerySpecification.withStatement("""
                        MATCH (c:Composer {id: 'composer-beethoven'})-[:COMPOSED]->(w:Work)
                        RETURN count(w)
                        """)
                        .transform(Long.class)
        );
        assertEquals(3, beethovenWorks, "Beethoven should have 3 works");
    }
}
