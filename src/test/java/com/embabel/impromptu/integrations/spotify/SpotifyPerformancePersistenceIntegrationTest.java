/*
 * Copyright 2024-2025 Embabel Pty Ltd.
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
package com.embabel.impromptu.integrations.spotify;

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.TestLlmConfiguration;
import com.embabel.impromptu.TestSecurityConfiguration;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivineTestConfig;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for SpotifyPerformance and SpotifyTrack persistence.
 * Verifies that performances and tracks can be saved to and loaded from Neo4j,
 * with their HAS_TRACK relationships properly maintained.
 * <p>
 * Uses Testcontainers for Neo4j (via @EnableDrivineTestConfig).
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
class SpotifyPerformancePersistenceIntegrationTest {

    @Autowired
    private PersistenceManager persistenceManager;

    @Autowired
    private NamedEntityDataRepository namedEntityDataRepository;

    private static final String TEST_PERFORMANCE_ID = "test-album:test-track-1";
    private static final String TEST_WORK_ID = "work-brahms-symphony4";

    @BeforeEach
    void setUp() {
        clearTestData();
        createTestData();
    }

    @AfterEach
    void tearDown() {
        clearTestData();
    }

    private void clearTestData() {
        persistenceManager.execute(
                QuerySpecification.withStatement(
                        "MATCH (n) WHERE n.primarySource = 'spotify-test' DETACH DELETE n"
                )
        );
    }

    private void createTestData() {
        // Create SpotifyTrack nodes
        var tracks = List.of(
                Map.of(
                        "id", "spotify:track:track1",
                        "uri", "spotify:track:track1",
                        "name", "Symphony No. 4: I. Allegro non troppo",
                        "artist", "Berlin Philharmonic",
                        "durationMs", 720000
                ),
                Map.of(
                        "id", "spotify:track:track2",
                        "uri", "spotify:track:track2",
                        "name", "Symphony No. 4: II. Andante moderato",
                        "artist", "Berlin Philharmonic",
                        "durationMs", 660000
                ),
                Map.of(
                        "id", "spotify:track:track3",
                        "uri", "spotify:track:track3",
                        "name", "Symphony No. 4: III. Allegro giocoso",
                        "artist", "Berlin Philharmonic",
                        "durationMs", 360000
                ),
                Map.of(
                        "id", "spotify:track:track4",
                        "uri", "spotify:track:track4",
                        "name", "Symphony No. 4: IV. Allegro energico",
                        "artist", "Berlin Philharmonic",
                        "durationMs", 600000
                )
        );

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                                UNWIND $tracks AS t
                                MERGE (track:__Entity__:SpotifyTrack:Reference {id: t.id})
                                SET track.name = t.name,
                                    track.uri = t.uri,
                                    track.artist = t.artist,
                                    track.durationMs = t.durationMs,
                                    track.primarySource = 'spotify-test'
                                """)
                        .bind(Map.of("tracks", tracks))
        );

        // Create SpotifyPerformance node
        persistenceManager.execute(
                QuerySpecification.withStatement("""
                                MERGE (perf:__Entity__:SpotifyPerformance:Reference {id: $id})
                                SET perf.name = $name,
                                    perf.workId = $workId,
                                    perf.albumId = $albumId,
                                    perf.albumName = $albumName,
                                    perf.performer = $performer,
                                    perf.ensemble = $ensemble,
                                    perf.conductor = $conductor,
                                    perf.primarySource = 'spotify-test'
                                """)
                        .bind(Map.of(
                                "id", TEST_PERFORMANCE_ID,
                                "name", "Karajan / Berlin Philharmonic",
                                "workId", TEST_WORK_ID,
                                "albumId", "test-album",
                                "albumName", "Brahms: Symphony No. 4",
                                "performer", "",
                                "ensemble", "Berlin Philharmonic",
                                "conductor", "Herbert von Karajan"
                        ))
        );

        // Create HAS_TRACK relationships
        persistenceManager.execute(
                QuerySpecification.withStatement("""
                                MATCH (perf:SpotifyPerformance {id: $perfId})
                                MATCH (track:SpotifyTrack)
                                WHERE track.primarySource = 'spotify-test'
                                MERGE (perf)-[:HAS_TRACK]->(track)
                                """)
                        .bind(Map.of("perfId", TEST_PERFORMANCE_ID))
        );
    }

    @Test
    void testSpotifyTracksPersisted() {
        var count = persistenceManager.getOne(
                QuerySpecification.withStatement(
                                "MATCH (t:SpotifyTrack) WHERE t.primarySource = 'spotify-test' RETURN count(t)")
                        .transform(Long.class)
        );
        assertEquals(4L, count, "Should have 4 test tracks");
    }

    @Test
    void testSpotifyPerformancePersisted() {
        var count = persistenceManager.getOne(
                QuerySpecification.withStatement(
                                "MATCH (p:SpotifyPerformance) WHERE p.primarySource = 'spotify-test' RETURN count(p)")
                        .transform(Long.class)
        );
        assertEquals(1L, count, "Should have 1 test performance");
    }

    @Test
    void testHasTrackRelationships() {
        var trackCount = persistenceManager.getOne(
                QuerySpecification.withStatement("""
                                MATCH (p:SpotifyPerformance {id: $perfId})-[:HAS_TRACK]->(t:SpotifyTrack)
                                RETURN count(t)
                                """)
                        .bind(Map.of("perfId", TEST_PERFORMANCE_ID))
                        .transform(Long.class)
        );
        assertEquals(4L, trackCount, "Performance should have 4 tracks via HAS_TRACK relationship");
    }

    @Test
    @Transactional
    void testLoadSpotifyTrack() {
        var track = namedEntityDataRepository.findById("spotify:track:track1", SpotifyTrack.class);
        assertNotNull(track, "Should be able to load SpotifyTrack by ID");
        assertEquals("Symphony No. 4: I. Allegro non troppo", track.getName());
        assertEquals("spotify:track:track1", track.getUri());
        assertEquals("Berlin Philharmonic", track.getArtist());
        assertEquals(720000L, track.getDurationMs());
    }

    @Test
    @Transactional
    void testSpotifyTrackPlayableMethods() {
        var track = namedEntityDataRepository.findById("spotify:track:track1", SpotifyTrack.class);
        assertNotNull(track);

        // Test Playable interface methods
        assertEquals("Symphony No. 4: I. Allegro non troppo", track.title());
        assertEquals(720, track.durationSeconds());
        assertEquals("12:00", track.durationFormatted());
        assertEquals("https://open.spotify.com/track/track1", track.url());
        assertEquals("spotify", track.source());
    }

    @Test
    @Transactional
    void testLoadSpotifyPerformance() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, SpotifyPerformance.class);
        assertNotNull(performance, "Should be able to load SpotifyPerformance by ID");
        assertEquals("test-album", performance.getAlbumId());
        assertEquals("Brahms: Symphony No. 4", performance.albumName());
        assertEquals("Berlin Philharmonic", performance.ensemble());
        assertEquals("Herbert von Karajan", performance.conductor());
    }

    @Test
    @Transactional
    void testLoadSpotifyPerformanceWithTracks() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, SpotifyPerformance.class);
        assertNotNull(performance);

        var tracks = performance.tracks();
        assertNotNull(tracks, "Performance should have tracks loaded via relationship");
        assertEquals(4, tracks.size(), "Performance should have 4 tracks");

        // Verify track data is populated
        var firstTrack = tracks.stream()
                .filter(t -> t.getId().equals("spotify:track:track1"))
                .findFirst()
                .orElse(null);
        assertNotNull(firstTrack, "Should find first track");
        assertEquals("Symphony No. 4: I. Allegro non troppo", firstTrack.getName());
    }

    @Test
    @Transactional
    void testPerformanceDurationCalculation() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, SpotifyPerformance.class);
        assertNotNull(performance);

        // Total duration should be sum of all tracks: 720 + 660 + 360 + 600 = 2340 seconds
        int expectedDuration = (720000 + 660000 + 360000 + 600000) / 1000;
        assertEquals(expectedDuration, performance.durationSeconds(),
                "Performance duration should be sum of track durations");
    }

    @Test
    @Transactional
    void testPerformanceTrackUris() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, SpotifyPerformance.class);
        assertNotNull(performance);

        var uris = performance.trackUris();
        assertNotNull(uris);
        assertEquals(4, uris.size());
        assertTrue(uris.contains("spotify:track:track1"));
        assertTrue(uris.contains("spotify:track:track2"));
        assertTrue(uris.contains("spotify:track:track3"));
        assertTrue(uris.contains("spotify:track:track4"));
    }

    @Test
    @Transactional
    void testPerformanceUrl() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, SpotifyPerformance.class);
        assertNotNull(performance);
        assertEquals("https://open.spotify.com/album/test-album", performance.url());
    }

    @Test
    @Transactional
    void testPerformanceTitle() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, SpotifyPerformance.class);
        assertNotNull(performance);
        // Title builds from performer/ensemble/conductor
        String title = performance.title();
        assertTrue(title.contains("Berlin Philharmonic"), "Title should include ensemble");
        assertTrue(title.contains("Karajan"), "Title should include conductor");
    }
}
