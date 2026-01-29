/*
 * Copyright 2024-2026 Embabel Pty Ltd.
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
package com.embabel.impromptu.integrations;

import com.embabel.agent.api.tool.AgenticTool;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentProcess;
import com.embabel.impromptu.integrations.spotify.SpotifyPerformanceImpl;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.spotify.SpotifyTrack;
import com.embabel.impromptu.integrations.spotify.SpotifyTrackImpl;
import com.embabel.impromptu.integrations.youtube.YouTubePerformanceImpl;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Service for finding and assembling performances of classical works.
 * <p>
 * Uses an {@link AgenticTool} that orchestrates searches across Spotify and YouTube,
 * using the LLM to:
 * <ul>
 *   <li>Construct appropriate search queries for the work</li>
 *   <li>Parse performer/conductor/ensemble from track metadata</li>
 *   <li>Group tracks into coherent performances</li>
 *   <li>Return structured Performance objects</li>
 * </ul>
 */
@Service
public class PerformanceAssemblyService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAssemblyService.class);
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/performance/assembly_system.jinja";
    private static final String SINGLE_RESULT_PROMPT_PATH = "classpath:prompts/performance/single_result_system.jinja";

    private final SpotifyService spotifyService;
    private final YouTubeService youTubeService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerformanceAssemblyService(SpotifyService spotifyService,
                                      YouTubeService youTubeService,
                                      ResourceLoader resourceLoader) {
        this.spotifyService = spotifyService;
        this.youTubeService = youTubeService;
        this.resourceLoader = resourceLoader;
    }

    private String loadSystemPrompt() {
        try {
            var resource = resourceLoader.getResource(SYSTEM_PROMPT_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load system prompt from {}", SYSTEM_PROMPT_PATH, e);
            throw new IllegalStateException("Failed to load performance assembly system prompt", e);
        }
    }

    private String loadSingleResultPrompt() {
        try {
            var resource = resourceLoader.getResource(SINGLE_RESULT_PROMPT_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load single-result system prompt from {}", SINGLE_RESULT_PROMPT_PATH, e);
            throw new IllegalStateException("Failed to load single-result performance assembly system prompt", e);
        }
    }

    /**
     * Create an AgenticTool configured for finding performances.
     * This tool can be used in an agent action's tool loop.
     *
     * @param user The user (needed for Spotify API access)
     * @return Configured AgenticTool
     */
    public Tool createPerformanceFinderTool(ImpromptuUser user) {
        return new AgenticTool(
                "findPerformances",
                """
                        Find performances of a classical work on streaming platforms.
                        Returns structured performance data including performers, conductors, and track lists.
                        IMPORTANT: Set the 'platforms' parameter based on the user's request.
                        """
        )
                .withTools(tools(user).toArray(new Tool[0]))
                .withSystemPrompt(loadSystemPrompt())
                .withParameter(Tool.Parameter.string(
                        "workQuery",
                        "The work to search for, e.g., 'Glazunov Violin Concerto' or 'Brahms Symphony No. 4'"
                ))
                .withParameter(Tool.Parameter.string(
                        "platforms",
                        "Comma-separated list of platforms to search. Use 'youtube' for YouTube, 'spotify' for Spotify. E.g., 'youtube' or 'youtube,spotify'"
                ));
    }

    /**
     * Create a single-result performance finder for concert assembly.
     * Uses a specialized prompt that emphasizes picking ONE best recording per work.
     */
    public Tool createSingleResultPerformanceFinderTool(ImpromptuUser user) {
        return new AgenticTool(
                "findPerformances",
                """
                        Find ONE performance of a classical work on streaming platforms.
                        IMPORTANT: Search, pick the BEST recording, create ONE performance, then STOP.
                        Do NOT create multiple performances for the same work.
                        """
        )
                .withTools(tools(user).toArray(new Tool[0]))
                .withSystemPrompt(loadSingleResultPrompt())
                .withParameter(Tool.Parameter.string(
                        "workQuery",
                        "The work to search for, e.g., 'Glazunov Violin Concerto' or 'Brahms Symphony No. 4'"
                ))
                .withParameter(Tool.Parameter.string(
                        "platforms",
                        "Comma-separated list of platforms to search. Use 'youtube' for YouTube, 'spotify' for Spotify. E.g., 'youtube' or 'youtube,spotify'"
                ));
    }

    /**
     * Check if performance finding is available (at least one platform configured).
     */
    public boolean isAvailable(ImpromptuUser user) {
        boolean spotifyAvailable = spotifyService.isConfigured() && spotifyService.isLinked(user);
        boolean youtubeAvailable = youTubeService.isConfigured();
        return spotifyAvailable || youtubeAvailable;
    }

    /**
     * Store a performance in the blackboard for later retrieval by createConcert.
     */
    private void storePerformance(Performance<?> performance) {
        var process = AgentProcess.get();
        if (process != null) {
            process.getBlackboard().addObject(performance);
            logger.debug("Stored performance in blackboard: {}", performance.title());
        }
    }

    /**
     * Get all tools for the given user.
     */
    private List<Tool> tools(ImpromptuUser user) {
        var tools = new LinkedList<Tool>();

        if (spotifyService.isConfigured() && spotifyService.isLinked(user)) {
            tools.add(searchSpotifyTracksTool(user));
            tools.add(getSpotifyAlbumTracksTool(user));
            tools.add(createSpotifyPerformanceTool(user));
        }

        if (youTubeService.isConfigured()) {
            tools.add(searchYouTubeVideosTool());
            tools.add(createYouTubePerformanceTool());
        }

        return tools;
    }

    private Tool searchSpotifyTracksTool(ImpromptuUser user) {
        return Tool.create(
                "searchSpotifyTracks",
                """
                        Search Spotify for tracks matching a query.
                        Returns track details including name, artist, album, and duration.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("query", "Search query for tracks")
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var query = (String) params.get("query");
                        if (query == null || query.isBlank()) {
                            return Tool.Result.text("Error: query is required");
                        }

                        var tracks = spotifyService.searchTracksDetailed(user, query, 15);
                        logger.info("Spotify search '{}' returned {} tracks", query, tracks.size());

                        var trackInfos = tracks.stream()
                                .map(t -> new TrackInfo(
                                        t.uri(),
                                        t.name(),
                                        t.artist(),
                                        String.join(", ", t.allArtists()),
                                        t.albumName(),
                                        t.albumId(),
                                        t.durationSeconds(),
                                        t.trackNumber()
                                ))
                                .toList();
                        return Tool.Result.text(toJson(trackInfos));
                    } catch (Exception e) {
                        logger.error("Spotify search failed", e);
                        return Tool.Result.text("Error: %s".formatted(e.getMessage()));
                    }
                }
        );
    }

    private Tool getSpotifyAlbumTracksTool(ImpromptuUser user) {
        return Tool.create(
                "getSpotifyAlbumTracks",
                """
                        Get all tracks from a Spotify album.
                        Use this to find all movements of a work on an album.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("albumId", "Spotify album ID")
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var albumId = (String) params.get("albumId");
                        if (albumId == null || albumId.isBlank()) {
                            return Tool.Result.text("Error: albumId is required");
                        }

                        var tracks = spotifyService.getAlbumTracks(user, albumId);
                        logger.info("Got {} tracks from album {}", tracks.size(), albumId);

                        var trackInfos = tracks.stream()
                                .map(t -> new AlbumTrackInfo(
                                        t.uri(),
                                        t.name(),
                                        t.artist(),
                                        t.trackNumber(),
                                        t.discNumber()
                                ))
                                .toList();
                        return Tool.Result.text(toJson(trackInfos));
                    } catch (Exception e) {
                        logger.error("Failed to get album tracks", e);
                        return Tool.Result.text("Error: %s".formatted(e.getMessage()));
                    }
                }
        );
    }

    private Tool createSpotifyPerformanceTool(ImpromptuUser user) {
        return Tool.create(
                "createSpotifyPerformance",
                """
                        Create a Spotify performance object with the given details.
                        Call this once you've identified a performance.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("workName", "Name of the work being performed, e.g. 'Brahms Piano Trio No. 1'"),
                        Tool.Parameter.string("albumId", "Spotify album ID"),
                        Tool.Parameter.string("albumName", "Album name"),
                        Tool.Parameter.string("performer", "Primary performer name", false),
                        Tool.Parameter.string("ensemble", "Orchestra or ensemble name", false),
                        Tool.Parameter.string("conductor", "Conductor name", false),
                        Tool.Parameter.string("trackUris", "Comma-separated list of Spotify track URIs")
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var workName = (String) params.get("workName");
                        var albumId = (String) params.get("albumId");
                        var albumName = (String) params.get("albumName");
                        var performer = (String) params.get("performer");
                        var ensemble = (String) params.get("ensemble");
                        var conductor = (String) params.get("conductor");
                        var trackUrisStr = (String) params.get("trackUris");

                        if (trackUrisStr == null || trackUrisStr.isBlank()) {
                            return Tool.Result.text("Error: trackUris is required");
                        }

                        var trackUris = Arrays.stream(trackUrisStr.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList();

                        if (trackUris.isEmpty()) {
                            return Tool.Result.text("Error: No valid track URIs provided");
                        }

                        // Create tracks from the URIs
                        var tracks = trackUris.stream()
                                .<SpotifyTrack>map(uri -> SpotifyTrackImpl.fromApi(
                                        uri, "Track", performer != null ? performer : "Unknown", 0))
                                .toList();

                        var performance = SpotifyPerformanceImpl.create(
                                null, workName, albumId, albumName, performer, ensemble, conductor, tracks
                        );

                        logger.info("Created Spotify performance: {} - {} ({} tracks)",
                                performance.title(), performance.albumName(), tracks.size());

                        storePerformance(performance);

                        var message = "Created Spotify performance: %s (%d tracks) - %s"
                                .formatted(performance.title(), tracks.size(), performance.url());

                        return Tool.Result.withArtifact(message, performance);
                    } catch (Exception e) {
                        logger.error("Failed to create Spotify performance", e);
                        return Tool.Result.text("Error creating performance: %s".formatted(e.getMessage()));
                    }
                }
        );
    }

    private Tool searchYouTubeVideosTool() {
        return Tool.create(
                "searchYouTubeVideos",
                """
                        Search YouTube for videos matching a query.
                        Returns video details including title, channel, and duration.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("query", "Search query for videos")
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var query = (String) params.get("query");
                        if (query == null || query.isBlank()) {
                            return Tool.Result.text("Error: query is required");
                        }

                        var videos = youTubeService.searchVideosDetailed(query, 10);
                        logger.info("YouTube search '{}' returned {} videos", query, videos.size());

                        var videoInfos = videos.stream()
                                .map(v -> new VideoInfo(
                                        v.getVideoId(),
                                        v.getTitle(),
                                        v.getChannelTitle(),
                                        v.durationSeconds(),
                                        v.durationFormatted()
                                ))
                                .toList();
                        return Tool.Result.text(toJson(videoInfos));
                    } catch (Exception e) {
                        logger.error("YouTube search failed", e);
                        return Tool.Result.text("Error: %s".formatted(e.getMessage()));
                    }
                }
        );
    }

    private Tool createYouTubePerformanceTool() {
        return Tool.create(
                "createYouTubePerformance",
                """
                        Create a YouTube performance object with the given details.
                        Call this once you've identified a performance.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("workName", "Name of the work being performed, e.g. 'Brahms Piano Trio No. 1'"),
                        Tool.Parameter.string("videoId", "YouTube video ID"),
                        Tool.Parameter.string("performer", "Primary performer name", false),
                        Tool.Parameter.string("ensemble", "Orchestra or ensemble name", false),
                        Tool.Parameter.string("conductor", "Conductor name", false)
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var workName = (String) params.get("workName");
                        var videoId = (String) params.get("videoId");
                        var performer = (String) params.get("performer");
                        var ensemble = (String) params.get("ensemble");
                        var conductor = (String) params.get("conductor");

                        if (videoId == null || videoId.isBlank()) {
                            return Tool.Result.text("Error: videoId is required");
                        }

                        var video = youTubeService.getVideoDetails(videoId);
                        if (video == null) {
                            return Tool.Result.text("Error: Video not found: %s".formatted(videoId));
                        }

                        var performance = YouTubePerformanceImpl.create(
                                null, workName, performer, ensemble, conductor, video
                        );

                        logger.info("Created YouTube performance: {} - {}",
                                performance.title(), video.getTitle());

                        storePerformance(performance);

                        var message = "Created YouTube performance: %s - %s"
                                .formatted(performance.title(), performance.url());

                        return Tool.Result.withArtifact(message, performance);
                    } catch (Exception e) {
                        logger.error("Failed to create YouTube performance", e);
                        return Tool.Result.text("Error creating performance: %s".formatted(e.getMessage()));
                    }
                }
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    // ========== DTOs for JSON serialization ==========

    record TrackInfo(
            String uri,
            String name,
            String artist,
            String allArtists,
            String albumName,
            String albumId,
            int durationSeconds,
            int trackNumber
    ) {}

    record AlbumTrackInfo(
            String uri,
            String name,
            String artist,
            int trackNumber,
            int discNumber
    ) {}

    record VideoInfo(
            String videoId,
            String title,
            String channelTitle,
            int durationSeconds,
            String durationFormatted
    ) {}
}
