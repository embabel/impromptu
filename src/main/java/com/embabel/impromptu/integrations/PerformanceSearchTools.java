package com.embabel.impromptu.integrations;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.impromptu.integrations.spotify.SpotifyPerformance;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubePerformance;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Sub-tools for the Performance finder AgenticTool.
 * These tools are used by the LLM to search for and gather performance data
 * from Spotify and YouTube.
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

    // ========== Spotify Tools ==========

    @LlmTool(description = "Search Spotify for tracks matching a query. Returns track details including name, artist, album, and duration.")
    public String searchSpotifyTracks(String query) {
        if (!spotifyService.isConfigured() || !spotifyService.isLinked(user)) {
            return "Spotify not available";
        }

        try {
            var tracks = spotifyService.searchTracksDetailed(user, query, 15);
            logger.info("Spotify search '{}' returned {} tracks", query, tracks.size());
            return toJson(tracks.stream()
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
                    .toList());
        } catch (Exception e) {
            logger.error("Spotify search failed", e);
            return "Error: " + e.getMessage();
        }
    }

    @LlmTool(description = "Get all tracks from a Spotify album. Use this to find all movements of a work on an album.")
    public String getSpotifyAlbumTracks(String albumId) {
        if (!spotifyService.isConfigured() || !spotifyService.isLinked(user)) {
            return "Spotify not available";
        }

        try {
            var tracks = spotifyService.getAlbumTracks(user, albumId);
            logger.info("Got {} tracks from album {}", tracks.size(), albumId);
            return toJson(tracks.stream()
                    .map(t -> new AlbumTrackInfo(
                            t.uri(),
                            t.name(),
                            t.artist(),
                            t.trackNumber(),
                            t.discNumber()
                    ))
                    .toList());
        } catch (Exception e) {
            logger.error("Failed to get album tracks", e);
            return "Error: " + e.getMessage();
        }
    }

    @LlmTool(description = "Create a Spotify performance object with the given details. Call this once you've identified a performance. Pass track URIs as comma-separated string.")
    public String createSpotifyPerformance(
            String workId,
            String albumId,
            String albumName,
            String performer,
            String ensemble,
            String conductor,
            String trackUrisCommaSeparated
    ) {
        if (!spotifyService.isConfigured() || !spotifyService.isLinked(user)) {
            return "Spotify not available";
        }

        try {
            // Parse comma-separated track URIs
            var trackUris = java.util.Arrays.stream(trackUrisCommaSeparated.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (trackUris.isEmpty()) {
                return "Error: No track URIs provided";
            }

            // Create tracks from the URIs with basic info
            var tracks = trackUris.stream()
                    .map(uri -> new SpotifyService.SpotifyTrack(uri, "Track", performer != null ? performer : "Unknown", 0))
                    .toList();

            var performance = new SpotifyPerformance(
                    workId, albumId, albumName, performer, ensemble, conductor, tracks
            );

            logger.info("Created Spotify performance: {} - {}", performance.title(), performance.albumName());
            return toJson(new PerformanceResult(
                    "spotify",
                    performance.id(),
                    performance.title(),
                    performance.albumName(),
                    performer,
                    ensemble,
                    conductor,
                    performance.durationSeconds(),
                    performance.url(),
                    trackUris.size()
            ));
        } catch (Exception e) {
            logger.error("Failed to create performance", e);
            return "Error: " + e.getMessage();
        }
    }

    // ========== YouTube Tools ==========

    @LlmTool(description = "Search YouTube for videos matching a query. Returns video details including title, channel, and duration.")
    public String searchYouTubeVideos(String query) {
        if (!youTubeService.isConfigured()) {
            return "YouTube not available";
        }

        try {
            var videos = youTubeService.searchVideosDetailed(query, 10);
            logger.info("YouTube search '{}' returned {} videos", query, videos.size());
            return toJson(videos.stream()
                    .map(v -> new VideoInfo(
                            v.videoId(),
                            v.title(),
                            v.channelTitle(),
                            v.durationSeconds(),
                            v.durationFormatted()
                    ))
                    .toList());
        } catch (Exception e) {
            logger.error("YouTube search failed", e);
            return "Error: " + e.getMessage();
        }
    }

    @LlmTool(description = "Create a YouTube performance object with the given details. Call this once you've identified a performance.")
    public String createYouTubePerformance(
            String workId,
            String videoId,
            String performer,
            String ensemble,
            String conductor
    ) {
        if (!youTubeService.isConfigured()) {
            return "YouTube not available";
        }

        try {
            var video = youTubeService.getVideoDetails(videoId);
            if (video == null) {
                return "Video not found: " + videoId;
            }

            var performance = new YouTubePerformance(
                    workId, performer, ensemble, conductor, video
            );

            logger.info("Created YouTube performance: {} - {}", performance.title(), video.title());
            return toJson(new PerformanceResult(
                    "youtube",
                    performance.id(),
                    performance.title(),
                    video.title(),
                    performer,
                    ensemble,
                    conductor,
                    performance.durationSeconds(),
                    performance.url(),
                    1
            ));
        } catch (Exception e) {
            logger.error("Failed to create performance", e);
            return "Error: " + e.getMessage();
        }
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

    record PerformanceResult(
            String source,
            String id,
            String title,
            String albumOrVideoTitle,
            String performer,
            String ensemble,
            String conductor,
            int durationSeconds,
            String url,
            int trackCount
    ) {}
}
