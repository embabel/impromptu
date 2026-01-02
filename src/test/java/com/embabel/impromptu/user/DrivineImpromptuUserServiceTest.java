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

import com.embabel.impromptu.TestDrivineConfiguration;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestDrivineConfiguration.class)
@ActiveProfiles("test")
@Transactional
class DrivineImpromptuUserServiceTest {

    @Autowired
    private GraphObjectManager graphObjectManager;

    @Autowired
    private PersistenceManager persistenceManager;

    private DrivineImpromptuUserService userService;

    @BeforeEach
    void setUp() {
        userService = new DrivineImpromptuUserService(graphObjectManager);
        // Clean up any existing test users
        persistenceManager.execute(
                QuerySpecification.withStatement("MATCH (u:User) WHERE u.id STARTS WITH 'test-' DETACH DELETE u")
        );
    }

    @Test
    void saveAndFindById() {
        // Given
        var user = new ImpromptuUser("test-id-1", "Test User", "testuser", "test@example.com");

        // When
        userService.save(user);
        var found = userService.findById("test-id-1");

        // Then
        assertNotNull(found);
        assertEquals("test-id-1", found.getId());
        assertEquals("Test User", found.getDisplayName());
        assertEquals("testuser", found.getUsername());
        assertEquals("test@example.com", found.getEmail());
    }

    @Test
    void findByIdReturnsNullWhenNotFound() {
        // When
        var found = userService.findById("non-existent-id");

        // Then
        assertNull(found);
    }

    @Test
    void saveCreatesNodeInNeo4j() {
        // Given
        var user = new ImpromptuUser("test-id-2", "Neo4j User", "neouser", "neo@example.com");

        // When
        userService.save(user);

        // Then - verify directly with Cypher
        var count = persistenceManager.getOne(
                QuerySpecification
                        .withStatement("MATCH (u:User {id: $id}) RETURN count(u)")
                        .bind(Map.of("id", "test-id-2"))
                        .transform(Long.class)
        );
        assertEquals(1L, count);
    }

    @Test
    void saveUpdatesExistingUser() {
        // Given - save a user first
        var existing = new ImpromptuUser("test-existing", "Original Name", "existing", "existing@example.com");
        userService.save(existing);

        // When - save again with same id but different display name
        var updated = new ImpromptuUser("test-existing", "Updated Name", "existing", "existing@example.com");
        userService.save(updated);

        // Then - should have the updated name, and only one node
        var found = userService.findById("test-existing");
        assertNotNull(found);
        assertEquals("Updated Name", found.getDisplayName());

        // Verify only one node exists
        var count = persistenceManager.getOne(
                QuerySpecification
                        .withStatement("MATCH (u:User {id: $id}) RETURN count(u)")
                        .bind(Map.of("id", "test-existing"))
                        .transform(Long.class)
        );
        assertEquals(1L, count);
    }

    @Test
    void findByUsername() {
        // Given
        var user = new ImpromptuUser("test-id-3", "Username Test", "uniqueuser", "unique@example.com");
        userService.save(user);

        // When
        var found = userService.findByUsername("uniqueuser");

        // Then
        assertNotNull(found);
        assertEquals("test-id-3", found.getId());
        assertEquals("uniqueuser", found.getUsername());
    }

    @Test
    void findByUsernameReturnsNullWhenNotFound() {
        // When
        var found = userService.findByUsername("nonexistent");

        // Then
        assertNull(found);
    }

    @Test
    void findByEmail() {
        // Given
        var user = new ImpromptuUser("test-id-4", "Email Test", "emailuser", "findme@example.com");
        userService.save(user);

        // When
        var found = userService.findByEmail("findme@example.com");

        // Then
        assertNotNull(found);
        assertEquals("test-id-4", found.getId());
        assertEquals("findme@example.com", found.getEmail());
    }

    @Test
    void findByEmailReturnsNullWhenNotFound() {
        // When
        var found = userService.findByEmail("nobody@example.com");

        // Then
        assertNull(found);
    }
}