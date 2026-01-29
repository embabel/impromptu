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
import org.springframework.stereotype.Service;

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

    private static final String SYSTEM_PROMPT = """
            You are a classical music expert finding performances of classical works.

            IMPORTANT: The 'platforms' parameter tells you which platforms to search.
            Only search the platforms listed. Only use the tools for those platforms.
            For example, if platforms is "youtube", do NOT search Spotify.

            Given a work (composer and title), your task is to:
            1. Search for performances on the appropriate platform(s)
            2. For each result, identify:
               - The performer (soloist for concertos, lead musician for chamber music)
               - The ensemble/orchestra (if applicable)
               - The conductor (if applicable)
            3. Group tracks that belong to the same performance (same album, same performers)
            4. Create Performance objects for each distinct performance found

            Tips for parsing classical music metadata:
            - The "artist" field often contains the composer, not the performer
            - Look for performer names in the track title or album name
            - Common patterns: "Work - Performer, Orchestra, Conductor"
            - Multiple tracks with sequential numbers (I., II., III. or 1., 2., 3.) are movements
            - Movements of the same work share album ID and similar track names

            For Spotify:
            1. First search for tracks matching the work
            2. Use getSpotifyAlbumTracks to get all tracks from promising albums
            3. Identify which tracks are movements of the work
            4. Create a SpotifyPerformance with those track URIs

            For YouTube:
            1. Search for videos of the work
            2. Prefer videos with full performances (longer duration)
            3. Create a YouTubePerformance for each good result (aim for 3-5 performances)

            Return performances from the platform(s) requested by the user.
            """;

    private final SpotifyService spotifyService;
    private final YouTubeService youTubeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerformanceAssemblyService(SpotifyService spotifyService, YouTubeService youTubeService) {
        this.spotifyService = spotifyService;
        this.youTubeService = youTubeService;
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
                .withSystemPrompt(SYSTEM_PROMPT)
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
                        Returns the Performance as an artifact.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("workId", "ID of the work being performed", false),
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
                        var workId = (String) params.get("workId");
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
                                workId, albumId, albumName, performer, ensemble, conductor, tracks
                        );

                        logger.info("Created Spotify performance: {} - {} ({} tracks)",
                                performance.title(), performance.albumName(), tracks.size());

                        return Tool.Result.withArtifact(
                                """
                                        Created Spotify performance: %s (%d tracks) - %s
                                        """.formatted(performance.title(), tracks.size(), performance.url()).trim(),
                                performance
                        );
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
                        Returns the Performance as an artifact.
                        """,
                Tool.InputSchema.of(
                        Tool.Parameter.string("workId", "ID of the work being performed", false),
                        Tool.Parameter.string("videoId", "YouTube video ID"),
                        Tool.Parameter.string("performer", "Primary performer name", false),
                        Tool.Parameter.string("ensemble", "Orchestra or ensemble name", false),
                        Tool.Parameter.string("conductor", "Conductor name", false)
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var workId = (String) params.get("workId");
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
                                workId, performer, ensemble, conductor, video
                        );

                        logger.info("Created YouTube performance: {} - {}",
                                performance.title(), video.getTitle());

                        return Tool.Result.withArtifact(
                                """
                                        Created YouTube performance: %s - %s
                                        """.formatted(performance.title(), performance.url()).trim(),
                                performance
                        );
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
