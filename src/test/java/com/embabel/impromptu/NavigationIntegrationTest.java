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

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.domain.Composer;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivineTestConfig;
import org.drivine.manager.PersistenceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for navigating the knowledge graph.
 * Tests queries for composers, works, and their relationships.
 * <p>
 * Uses @TestInstance(PER_CLASS) to allow @BeforeAll with instance methods,
 * so test data is loaded once before all tests run.
 */
@SpringBootTest(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.ai.mcp.client.enabled=false",
                "impromptu.objective=test",
                "impromptu.behaviour=test",
                "impromptu.embedding-service=text-embedding-3-small",
        }
)
@EnableDrivine
@EnableDrivineTestConfig
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestLlmConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class NavigationIntegrationTest {

    @Autowired
    private PersistenceManager persistenceManager;

    private TestDataHelper testDataHelper;

    @Autowired
    private NamedEntityDataRepository namedEntityDataRepository;

    @BeforeAll
    void setUpTestData() {
        testDataHelper = new TestDataHelper(persistenceManager);
        testDataHelper.clearTestData();
        testDataHelper.createAllTestData();
    }

    @Test
    void testDataShouldBeLoaded() {
        assertEquals(5, testDataHelper.countComposers(), "Should have 5 test composers");
        assertEquals(9, testDataHelper.countWorks(), "Should have 9 test works");
    }

    @Test
    @Transactional
    void testLoadOneComposer() {
        var brahms = namedEntityDataRepository.findById("composer-brahms", Composer.class);
        assertNotNull(brahms);
        assertEquals(1833L, brahms.getBirthYear());
    }

    @Test
    @Transactional
    void testBusinessMethodOnComposer() {
        var brahms = namedEntityDataRepository.findById("composer-brahms", Composer.class);
        assertNotNull(brahms);
        // He actually was only 63 but this is what the math says, so this is a reasonable
        // test of this business method
        assertEquals(64L, brahms.lifespan());
    }

    @Test
    @Transactional
    void testWorks() {
        var brahms = namedEntityDataRepository.findById("composer-bach", Composer.class);
        var works = brahms.getWorks();
        assertNotNull(works);
        assertEquals(2, works.size(), "Bach should have 2 test works");
        for (var work : works) {
            assertNotNull(work.getTitle());
            System.out.println("Bach work: " + work.getTitle());
        }
    }
}
