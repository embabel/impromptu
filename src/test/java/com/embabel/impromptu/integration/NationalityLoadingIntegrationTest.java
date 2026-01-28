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
package com.embabel.impromptu.integration;

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.TestLlmConfiguration;
import com.embabel.impromptu.TestSecurityConfiguration;
import com.embabel.impromptu.domain.Nationality;
import com.embabel.impromptu.domain.Technique;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivineTestConfig;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for loading Nationality and Technique entities.
 * Helps debug issues with findAll for reference data entities.
 */
@SpringBootTest(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.ai.mcp.client.enabled=false",
                "impromptu.objective=test",
                "impromptu.behaviour=test",
                "impromptu.embedding-service=text-embedding-3-small",
                "impromptu.data-loading.open-opus=false",
        }
)
@EnableDrivine
@EnableDrivineTestConfig
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestLlmConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class NationalityLoadingIntegrationTest {

    @Autowired
    private PersistenceManager persistenceManager;

    @Autowired
    private NamedEntityDataRepository entityRepository;

    @BeforeAll
    void setUpTestData() {
        // Create test nationalities with __Entity__ label
        var nationalities = List.of(
                Map.of("id", "german", "name", "German", "country", "Germany", "code", "DE"),
                Map.of("id", "austrian", "name", "Austrian", "country", "Austria", "code", "AT"),
                Map.of("id", "french", "name", "French", "country", "France", "code", "FR")
        );

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $nationalities AS n
                        MERGE (nat:__Entity__:Nationality:Reference {id: n.id})
                        SET nat.name = n.name,
                            nat.country = n.country,
                            nat.countryCode = n.code
                        """)
                        .bind(Map.of("nationalities", nationalities))
        );

        // Create test techniques with __Entity__ label
        var techniques = List.of(
                Map.of("id", "counterpoint", "name", "Counterpoint"),
                Map.of("id", "serialism", "name", "Serialism")
        );

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $techniques AS t
                        MERGE (tech:__Entity__:Technique:Reference {id: t.id})
                        SET tech.name = t.name
                        """)
                        .bind(Map.of("techniques", techniques))
        );
    }

    @Test
    void shouldCountNationalitiesDirectly() {
        var count = persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (n:Nationality) RETURN count(n)")
                        .transform(Long.class)
        );
        System.out.println("Direct count of Nationality nodes: " + count);
        assertTrue(count >= 3, "Should have at least 3 nationalities");
    }

    @Test
    void shouldCountNationalitiesWithEntityLabel() {
        var count = persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (n:__Entity__:Nationality) RETURN count(n)")
                        .transform(Long.class)
        );
        System.out.println("Count of __Entity__:Nationality nodes: " + count);
        assertTrue(count >= 3, "Should have at least 3 nationalities with __Entity__ label");
    }

    @Test
    void shouldFindNationalitiesByLabel() {
        var results = entityRepository.findByLabel("Nationality");
        System.out.println("findByLabel('Nationality') returned " + results.size() + " results");
        for (var r : results) {
            System.out.println("  - " + r.getId() + ": " + r.getName() + " labels=" + r.labels());
        }
        assertFalse(results.isEmpty(), "findByLabel should find nationalities");
    }

    @Test
    void shouldFindAllNationalities() {
        var nationalities = entityRepository.findAll(Nationality.class);
        System.out.println("findAll(Nationality.class) returned " + nationalities.size() + " results");
        for (var n : nationalities) {
            System.out.println("  - " + n.getId() + ": " + n.getName());
        }
        assertFalse(nationalities.isEmpty(), "findAll should find nationalities");
    }

    @Test
    void shouldFindAllTechniques() {
        var techniques = entityRepository.findAll(Technique.class);
        System.out.println("findAll(Technique.class) returned " + techniques.size() + " results");
        for (var t : techniques) {
            System.out.println("  - " + t.getId() + ": " + t.getName());
        }
        assertFalse(techniques.isEmpty(), "findAll should find techniques");
    }

    @Test
    void debugEntityNodeName() {
        // Check what the entityNodeName property is set to
        System.out.println("entityRepository class: " + entityRepository.getClass().getName());

        // Try finding with explicit Entity label
        var withEntity = persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (n:Entity:Nationality) RETURN count(n)")
                        .transform(Long.class)
        );
        System.out.println("Count with Entity label: " + withEntity);

        var withDoubleUnderscoreEntity = persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (n:__Entity__:Nationality) RETURN count(n)")
                        .transform(Long.class)
        );
        System.out.println("Count with __Entity__ label: " + withDoubleUnderscoreEntity);
    }
}
