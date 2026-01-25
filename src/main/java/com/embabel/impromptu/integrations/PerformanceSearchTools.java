package com.embabel.impromptu.integrations;

import com.embabel.agent.api.tool.Tool;
import com.embabel.impromptu.integrations.spotify.SpotifyPerformance;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubePerformance;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Tools for the Performance finder AgenticTool.
 * These tools are used by the LLM to search for and gather performance data
 * from Spotify and YouTube.
 * <p>
 * The create*Performance tools return Performance objects as artifacts,
 * enabling the caller to access the structured data.
 */
public class PerformanceSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceSearchTools.class);

    private final SpotifyService spotifyService;
    private final YouTubeService youTubeService;
    private final ImpromptuUser user;
    private final ObjectMapper objectMapper;

    public PerformanceSearchTools(
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            ImpromptuUser user) {
        this.spotifyService = spotifyService;
        this.youTubeService = youTubeService;
        this.user = user;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all tools provided by this class.
     */
    public List<Tool> tools() {
        var tools = new LinkedList<Tool>();

        if (spotifyService.isConfigured() && spotifyService.isLinked(user)) {
            tools.add(searchSpotifyTracksTool());
            tools.add(getSpotifyAlbumTracksTool());
            tools.add(createSpotifyPerformanceTool());
        }

        if (youTubeService.isConfigured()) {
            tools.add(searchYouTubeVideosTool());
            tools.add(createYouTubePerformanceTool());
        }

        return tools;
    }

    private Tool searchSpotifyTracksTool() {
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

    private Tool getSpotifyAlbumTracksTool() {
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

    private Tool createSpotifyPerformanceTool() {
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
                                .map(uri -> new SpotifyService.SpotifyTrack(
                                        uri, "Track", performer != null ? performer : "Unknown", 0, Instant.now()))
                                .toList();

                        var performance = new SpotifyPerformance(
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
                                        v.videoId(),
                                        v.title(),
                                        v.channelTitle(),
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

                        var performance = new YouTubePerformance(
                                workId, performer, ensemble, conductor, video
                        );

                        logger.info("Created YouTube performance: {} - {}",
                                performance.title(), video.title());

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

    // ========== Helper methods ==========

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
    ) {
    }

    record AlbumTrackInfo(
            String uri,
            String name,
            String artist,
            int trackNumber,
            int discNumber
    ) {
    }

    record VideoInfo(
            String videoId,
            String title,
            String channelTitle,
            int durationSeconds,
            String durationFormatted
    ) {
    }
}
