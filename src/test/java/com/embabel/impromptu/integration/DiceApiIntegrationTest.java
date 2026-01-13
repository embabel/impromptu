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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the DICE REST API (proposition extraction and memory query).
 * <p>
 * These tests require a running Impromptu server with Open Opus data loaded.
 * They use real LLM calls, so expect some latency and cost.
 * <p>
 * Run with: {@code ./mvnw test -Dgroups=integration}
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Neo4j running (docker compose up -d)</li>
 *   <li>Impromptu server running (./mvnw spring-boot:run)</li>
 *   <li>Open Opus data loaded (curl -X POST http://localhost:8888/api/openopus/load)</li>
 * </ul>
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiceApiIntegrationTest {

    private static final String BASE_URL = System.getProperty("integration.baseUrl", "http://localhost:8888");
    private static final String API_KEY = System.getProperty("integration.apiKey", "impromptu-admin");
    private static final String TEST_CONTEXT = "integration_test_" + System.currentTimeMillis();

    private static final int TIMEOUT_SECONDS = 120; // LLM calls can be slow

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    DiceApiIntegrationTest() {
        // Configure longer timeout for LLM extraction calls
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-API-Key", API_KEY)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @BeforeAll
    void verifyServerRunning() {
        try {
            var response = restClient.get()
                    .uri("/api/documents/info")
                    .retrieve()
                    .toEntity(String.class);
            assertTrue(response.getStatusCode().is2xxSuccessful(),
                    "Server should be running at " + BASE_URL);
            System.out.println("Server verified running. Test context: " + TEST_CONTEXT);
        } catch (Exception e) {
            fail("Server not reachable at " + BASE_URL + ". Start with: ./mvnw spring-boot:run\n" + e.getMessage());
        }
    }

    @AfterAll
    void cleanup() {
        // Delete all propositions in test context
        try {
            var propositions = getPropositions();
            System.out.println("Cleaning up " + propositions.size() + " test propositions");
            for (var prop : propositions) {
                var id = prop.get("id").asText();
                restClient.delete()
                        .uri("/api/v1/contexts/{context}/memory/{id}", TEST_CONTEXT, id)
                        .retrieve()
                        .toBodilessEntity();
            }
        } catch (Exception e) {
            System.err.println("Cleanup failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // Extraction Tests
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Extract user learning about Bach piano")
    void extractLearningPreference() throws IOException {
        var request = loadTestData("learning-bach.json");

        var response = restClient.post()
                .uri("/api/v1/contexts/{context}/extract", TEST_CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "Extraction should succeed");

        var body = objectMapper.readTree(response.getBody());
        assertTrue(body.has("propositions"), "Response should contain propositions");

        var propositions = body.get("propositions");
        assertTrue(propositions.isArray() && !propositions.isEmpty(),
                "Should extract at least one proposition");

        // Entity assertions
        assertTrue(hasMentionNamed(propositions, "Bach"),
                "Should mention Bach");
        assertTrue(hasMentionOfType(propositions, "Composer"),
                "Should identify a Composer entity");
        assertTrue(hasResolvedEntity(propositions, "Bach"),
                "Bach should be resolved to an Open Opus entity");

        System.out.println("Extracted " + propositions.size() + " propositions about learning Bach:");
        for (var prop : propositions) {
            System.out.println("  - " + prop.get("text").asText());
        }
        printMentions(propositions);
    }

    @Test
    @Order(2)
    @DisplayName("Extract user preference with known entity")
    void extractUserPreference() throws IOException {
        var request = loadTestData("user-preference-brahms.json");

        var response = restClient.post()
                .uri("/api/v1/contexts/{context}/extract", TEST_CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var body = objectMapper.readTree(response.getBody());
        var propositions = body.get("propositions");
        assertTrue(!propositions.isEmpty(), "Should extract user preference proposition");

        // Entity assertions - user and composer
        assertTrue(hasMentionNamed(propositions, "Alice"),
                "Should mention Alice (the user)");
        assertTrue(hasMentionNamed(propositions, "Brahms"),
                "Should mention Brahms");
        assertTrue(hasResolvedEntity(propositions, "Brahms"),
                "Brahms should be resolved to an Open Opus entity");

        System.out.println("Extracted user preference propositions:");
        for (var prop : propositions) {
            System.out.println("  - " + prop.get("text").asText());
        }
        printMentions(propositions);
    }

    @Test
    @Order(3)
    @DisplayName("Extract multi-entity comparative statements")
    void extractMultiEntity() throws IOException {
        var request = loadTestData("multi-entity-romantic.json");

        var response = restClient.post()
                .uri("/api/v1/contexts/{context}/extract", TEST_CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var body = objectMapper.readTree(response.getBody());
        var propositions = body.get("propositions");
        assertFalse(propositions.isEmpty(), "Should extract propositions about Chopin and Liszt");

        // Entity assertions - both Romantic composers
        assertTrue(hasMentionNamed(propositions, "Chopin"),
                "Should mention Chopin");
        assertTrue(hasMentionNamed(propositions, "Liszt"),
                "Should mention Liszt");
        assertTrue(hasResolvedEntity(propositions, "Chopin"),
                "Chopin should be resolved to an Open Opus entity");
        assertTrue(hasResolvedEntity(propositions, "Liszt"),
                "Liszt should be resolved to an Open Opus entity");

        System.out.println("Extracted multi-entity propositions:");
        for (var prop : propositions) {
            System.out.println("  - " + prop.get("text").asText());
        }
        printMentions(propositions);
    }

    @Test
    @Order(4)
    @DisplayName("Extract opinion with user context")
    void extractOpinion() throws IOException {
        var request = loadTestData("opinion-beethoven.json");

        var response = restClient.post()
                .uri("/api/v1/contexts/{context}/extract", TEST_CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var body = objectMapper.readTree(response.getBody());
        var propositions = body.get("propositions");
        assertFalse(propositions.isEmpty(), "Should extract opinion propositions");

        // Entity assertions - Beethoven and user Bob
        assertTrue(hasMentionNamed(propositions, "Beethoven"),
                "Should mention Beethoven");
        assertTrue(hasMentionNamed(propositions, "Bob"),
                "Should mention Bob (the user)");
        assertTrue(hasResolvedEntity(propositions, "Beethoven"),
                "Beethoven should be resolved to an Open Opus entity");

        System.out.println("Extracted opinion propositions:");
        for (var prop : propositions) {
            System.out.println("  - " + prop.get("text").asText());
        }
        printMentions(propositions);
    }

    @Test
    @Order(5)
    @DisplayName("Extract general preferences (concerts, venues, lifestyle)")
    void extractGeneralPreferences() throws IOException {
        var request = loadTestData("general-preferences.json");

        var response = restClient.post()
                .uri("/api/v1/contexts/{context}/extract", TEST_CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var body = objectMapper.readTree(response.getBody());
        var propositions = body.get("propositions");
        assertFalse(propositions.isEmpty(), "Should extract general preference propositions");

        // Entity assertions - user Alice with non-music preferences
        assertTrue(hasMentionNamed(propositions, "Alice"),
                "Should mention Alice (the user)");

        System.out.println("Extracted general preference propositions:");
        for (var prop : propositions) {
            System.out.println("  - " + prop.get("text").asText());
        }
        printMentions(propositions);
    }

    // ========================================================================
    // Memory Query Tests
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("Query all propositions in context")
    void queryAllPropositions() throws IOException {
        var propositions = getPropositions();

        assertFalse(propositions.isEmpty(),
                "Should have propositions from earlier extraction tests");

        System.out.println("Total propositions in context: " + propositions.size());
    }

    @Test
    @Order(11)
    @DisplayName("Search propositions by similarity - piano preferences")
    void searchBySimilarity() throws IOException {
        var searchRequest = loadTestData("search-query-piano.json");

        var response = restClient.post()
                .uri("/api/v1/contexts/{context}/memory/search", TEST_CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(searchRequest)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var body = objectMapper.readTree(response.getBody());
        assertTrue(body.isArray(), "Search should return array of results");

        System.out.println("Similarity search results for piano preferences:");
        for (var result : body) {
            var score = result.has("score") ? result.get("score").asDouble() : 0.0;
            var text = result.has("proposition")
                    ? result.get("proposition").get("text").asText()
                    : result.get("text").asText();
            System.out.printf("  [%.3f] %s%n", score, text);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Get specific proposition by ID")
    void getPropositionById() throws IOException {
        var propositions = getPropositions();
        assertFalse(propositions.isEmpty(), "Need at least one proposition");

        var firstId = propositions.get(0).get("id").asText();

        var response = restClient.get()
                .uri("/api/v1/contexts/{context}/memory/{id}", TEST_CONTEXT, firstId)
                .retrieve()
                .toEntity(String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var body = objectMapper.readTree(response.getBody());
        assertEquals(firstId, body.get("id").asText());
        assertTrue(body.has("text"), "Proposition should have text");

        System.out.println("Retrieved proposition: " + body.get("text").asText());
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private String loadTestData(String filename) throws IOException {
        var resource = new ClassPathResource("data/" + filename);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private List<JsonNode> getPropositions() throws IOException {
        var response = restClient.get()
                .uri("/api/v1/contexts/{context}/memory", TEST_CONTEXT)
                .retrieve()
                .toEntity(String.class);

        var body = objectMapper.readTree(response.getBody());
        if (body.isArray()) {
            return objectMapper.convertValue(body,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
        }
        return List.of();
    }

    /**
     * Check if any proposition mentions an entity with the given name (case-insensitive partial match).
     */
    private boolean hasMentionNamed(JsonNode propositions, String name) {
        for (var prop : propositions) {
            var mentions = prop.get("mentions");
            if (mentions != null) {
                for (var mention : mentions) {
                    var mentionName = mention.get("name").asText().toLowerCase();
                    if (mentionName.contains(name.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if any proposition mentions an entity of the given type.
     */
    private boolean hasMentionOfType(JsonNode propositions, String type) {
        for (var prop : propositions) {
            var mentions = prop.get("mentions");
            if (mentions != null) {
                for (var mention : mentions) {
                    var mentionType = mention.get("type").asText();
                    if (type.equalsIgnoreCase(mentionType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if any proposition has a resolved entity (resolvedId is not null/empty).
     */
    private boolean hasResolvedEntity(JsonNode propositions, String nameContains) {
        for (var prop : propositions) {
            var mentions = prop.get("mentions");
            if (mentions != null) {
                for (var mention : mentions) {
                    var mentionName = mention.get("name").asText().toLowerCase();
                    var resolvedId = mention.has("resolvedId") ? mention.get("resolvedId").asText() : null;
                    if (mentionName.contains(nameContains.toLowerCase()) &&
                            resolvedId != null && !resolvedId.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Print all mentions from propositions for debugging.
     */
    private void printMentions(JsonNode propositions) {
        for (var prop : propositions) {
            var mentions = prop.get("mentions");
            if (mentions != null) {
                for (var mention : mentions) {
                    System.out.printf("    [%s] %s -> %s%n",
                            mention.get("type").asText(),
                            mention.get("name").asText(),
                            mention.has("resolvedId") ? mention.get("resolvedId").asText() : "unresolved");
                }
            }
        }
    }
}
