package com.embabel.impromptu.integrations.spotify;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of SpotifyPerformance for creating instances from Spotify API responses.
 */
public record SpotifyPerformanceImpl(
        String id,
        String workId,
        String albumId,
        String albumName,
        String performer,
        String ensemble,
        String conductor,
        List<SpotifyTrack> tracks,
        Instant timestamp
) implements SpotifyPerformance {

    /**
     * Create a SpotifyPerformance with a generated composite ID.
     */
    public static SpotifyPerformanceImpl create(
            String workId,
            String albumId,
            String albumName,
            String performer,
            String ensemble,
            String conductor,
            List<SpotifyTrack> tracks
    ) {
        String id = albumId + ":" + (tracks.isEmpty() ? "unknown" : tracks.getFirst().getId());
        Instant timestamp = tracks.isEmpty() ? Instant.now() : tracks.getFirst().getTimestamp();
        return new SpotifyPerformanceImpl(id, workId, albumId, albumName, performer, ensemble, conductor, tracks, timestamp);
    }

    @Override
    @NonNull
    public String getId() {
        return id;
    }

    @Override
    public String getAlbumId() {
        return albumId;
    }

    @Override
    public String albumName() {
        return albumName;
    }

    @Override
    public String performer() {
        return performer;
    }

    @Override
    public String ensemble() {
        return ensemble;
    }

    @Override
    public String conductor() {
        return conductor;
    }

    @Override
    public List<SpotifyTrack> tracks() {
        return tracks;
    }

    @Override
    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String title() {
        StringBuilder sb = new StringBuilder();
        if (performer != null && !performer.isBlank()) {
            sb.append(performer);
        }
        if (ensemble != null && !ensemble.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(ensemble);
        }
        if (conductor != null && !conductor.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(conductor);
        }
        return sb.isEmpty() ? albumName : sb.toString();
    }

    @Override
    public @NonNull String getDescription() {
        return title();
    }

    @Override
    public String playbackInfo() {
        var urisJson = trackUris().stream()
                .map(uri -> "\"" + uri + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return """
                {"source":"spotify","albumId":"%s","albumName":"%s","performer":"%s","ensemble":"%s","conductor":"%s","trackUris":[%s],"url":"%s","durationSeconds":%d,"trackCount":%d}
                """.formatted(
                albumId,
                albumName != null ? albumName : "",
                performer != null ? performer : "",
                ensemble != null ? ensemble : "",
                conductor != null ? conductor : "",
                urisJson,
                url(),
                durationSeconds(),
                tracks.size()
        ).trim();
    }
}
