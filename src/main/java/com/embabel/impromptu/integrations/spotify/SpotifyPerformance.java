package com.embabel.impromptu.integrations.spotify;

import com.embabel.impromptu.integrations.Performance;

import java.util.List;

/**
 * A performance on Spotify, typically consisting of multiple tracks (movements)
 * from the same album.
 */
public record SpotifyPerformance(
        String workId,
        String albumId,
        String albumName,
        String performer,
        String ensemble,
        String conductor,
        List<SpotifyService.SpotifyTrack> tracks
) implements Performance {

    @Override
    public String id() {
        // Composite ID: album + first track
        return albumId + ":" + (tracks.isEmpty() ? "unknown" : tracks.get(0).id());
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
    public String durationFormatted() {
        int total = durationSeconds();
        if (total <= 0) return null;
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    @Override
    public String url() {
        return "https://open.spotify.com/album/" + albumId;
    }

    @Override
    public String source() {
        return "spotify";
    }

    /**
     * Get all track URIs for playback.
     */
    public List<String> trackUris() {
        return tracks.stream()
                .map(SpotifyService.SpotifyTrack::uri)
                .toList();
    }
}
