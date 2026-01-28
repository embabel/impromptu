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
package com.embabel.impromptu.integrations.youtube;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for YouTubePerformance and YouTubeVideo persistence.
 * Verifies that performances and videos can be saved to and loaded from Neo4j,
 * with their HAS_VIDEO relationships properly maintained.
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
class YouTubePerformancePersistenceIntegrationTest {

    @Autowired
    private PersistenceManager persistenceManager;

    @Autowired
    private NamedEntityDataRepository namedEntityDataRepository;

    private static final String TEST_VIDEO_ID = "dQw4w9WgXcQ";
    private static final String TEST_PERFORMANCE_ID = TEST_VIDEO_ID;
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
                        "MATCH (n) WHERE n.primarySource = 'youtube-test' DETACH DELETE n"
                )
        );
    }

    private void createTestData() {
        // Create YouTubeVideo node
        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        MERGE (video:__Entity__:YouTubeVideo:Reference {id: $id})
                        SET video.name = $name,
                            video.videoId = $videoId,
                            video.title = $title,
                            video.channelTitle = $channelTitle,
                            video.videoDescription = $description,
                            video.thumbnailUrl = $thumbnailUrl,
                            video.durationSeconds = $durationSeconds,
                            video.primarySource = 'youtube-test'
                        """)
                        .bind(Map.of(
                                "id", TEST_VIDEO_ID,
                                "name", "Brahms Symphony No. 4 - Karajan / Berlin Philharmonic",
                                "videoId", TEST_VIDEO_ID,
                                "title", "Brahms Symphony No. 4 - Karajan / Berlin Philharmonic",
                                "channelTitle", "Deutsche Grammophon",
                                "description", "Herbert von Karajan conducts the Berlin Philharmonic",
                                "thumbnailUrl", "https://example.com/thumb.jpg",
                                "durationSeconds", 2400L  // 40 minutes
                        ))
        );

        // Create YouTubePerformance node
        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        MERGE (perf:__Entity__:YouTubePerformance:Reference {id: $id})
                        SET perf.name = $name,
                            perf.workId = $workId,
                            perf.videoId = $videoId,
                            perf.performer = $performer,
                            perf.ensemble = $ensemble,
                            perf.conductor = $conductor,
                            perf.primarySource = 'youtube-test'
                        """)
                        .bind(Map.of(
                                "id", TEST_PERFORMANCE_ID,
                                "name", "Karajan / Berlin Philharmonic",
                                "workId", TEST_WORK_ID,
                                "videoId", TEST_VIDEO_ID,
                                "performer", "",
                                "ensemble", "Berlin Philharmonic",
                                "conductor", "Herbert von Karajan"
                        ))
        );

        // Create HAS_VIDEO relationship
        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        MATCH (perf:YouTubePerformance {id: $perfId})
                        MATCH (video:YouTubeVideo {id: $videoId})
                        MERGE (perf)-[:HAS_VIDEO]->(video)
                        """)
                        .bind(Map.of("perfId", TEST_PERFORMANCE_ID, "videoId", TEST_VIDEO_ID))
        );
    }

    @Test
    void testYouTubeVideoPersisted() {
        var count = persistenceManager.getOne(
                QuerySpecification.withStatement(
                                "MATCH (v:YouTubeVideo) WHERE v.primarySource = 'youtube-test' RETURN count(v)")
                        .transform(Long.class)
        );
        assertEquals(1L, count, "Should have 1 test video");
    }

    @Test
    void testYouTubePerformancePersisted() {
        var count = persistenceManager.getOne(
                QuerySpecification.withStatement(
                                "MATCH (p:YouTubePerformance) WHERE p.primarySource = 'youtube-test' RETURN count(p)")
                        .transform(Long.class)
        );
        assertEquals(1L, count, "Should have 1 test performance");
    }

    @Test
    void testHasVideoRelationship() {
        var videoCount = persistenceManager.getOne(
                QuerySpecification.withStatement("""
                                MATCH (p:YouTubePerformance {id: $perfId})-[:HAS_VIDEO]->(v:YouTubeVideo)
                                RETURN count(v)
                                """)
                        .bind(Map.of("perfId", TEST_PERFORMANCE_ID))
                        .transform(Long.class)
        );
        assertEquals(1L, videoCount, "Performance should have 1 video via HAS_VIDEO relationship");
    }

    @Test
    @Transactional
    void testLoadYouTubeVideo() {
        var video = namedEntityDataRepository.findById(TEST_VIDEO_ID, YouTubeVideo.class);
        assertNotNull(video, "Should be able to load YouTubeVideo by ID");
        assertEquals("Brahms Symphony No. 4 - Karajan / Berlin Philharmonic", video.getTitle());
        assertEquals(TEST_VIDEO_ID, video.getVideoId());
        assertEquals("Deutsche Grammophon", video.getChannelTitle());
        assertEquals(2400L, video.getDurationSeconds());
    }

    @Test
    @Transactional
    void testYouTubeVideoPlayableMethods() {
        var video = namedEntityDataRepository.findById(TEST_VIDEO_ID, YouTubeVideo.class);
        assertNotNull(video);

        // Test Playable interface methods
        assertEquals("Brahms Symphony No. 4 - Karajan / Berlin Philharmonic", video.title());
        assertEquals(2400, video.durationSeconds());
        assertEquals("40:00", video.durationFormatted());
        assertEquals("https://www.youtube.com/watch?v=" + TEST_VIDEO_ID, video.url());
        assertEquals("youtube", video.source());
    }

    @Test
    @Transactional
    void testLoadYouTubePerformance() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, YouTubePerformance.class);
        assertNotNull(performance, "Should be able to load YouTubePerformance by ID");
        assertEquals(TEST_VIDEO_ID, performance.getVideoId());
        assertEquals("Berlin Philharmonic", performance.ensemble());
        assertEquals("Herbert von Karajan", performance.conductor());
    }

    @Test
    @Transactional
    void testLoadYouTubePerformanceWithVideo() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, YouTubePerformance.class);
        assertNotNull(performance);

        var videos = performance.tracks();
        assertNotNull(videos, "Performance should have videos loaded via relationship");
        assertEquals(1, videos.size(), "Performance should have 1 video");

        var video = videos.getFirst();
        assertNotNull(video, "Should have video");
        assertEquals("Brahms Symphony No. 4 - Karajan / Berlin Philharmonic", video.getTitle());
    }

    @Test
    @Transactional
    void testPerformanceDurationCalculation() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, YouTubePerformance.class);
        assertNotNull(performance);

        // Duration should be sum of all video durations (in this case, just the one video)
        assertEquals(2400, performance.durationSeconds(),
                "Performance duration should be the video duration");
    }

    @Test
    @Transactional
    void testPerformanceUrl() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, YouTubePerformance.class);
        assertNotNull(performance);
        assertEquals("https://www.youtube.com/watch?v=" + TEST_VIDEO_ID, performance.url());
    }

    @Test
    @Transactional
    void testPerformanceTitle() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, YouTubePerformance.class);
        assertNotNull(performance);
        // Title builds from performer/ensemble/conductor
        String title = performance.title();
        assertTrue(title.contains("Berlin Philharmonic"), "Title should include ensemble");
        assertTrue(title.contains("Karajan"), "Title should include conductor");
    }

    @Test
    @Transactional
    void testPerformanceAlbumName() {
        var performance = namedEntityDataRepository.findById(TEST_PERFORMANCE_ID, YouTubePerformance.class);
        assertNotNull(performance);
        // Album name for YouTube is the channel title
        assertEquals("Deutsche Grammophon", performance.albumName());
    }
}
