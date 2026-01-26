package com.embabel.impromptu.integrations.spotify;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

/**
 * Implementation of SpotifyTrack for creating instances from Spotify API responses.
 */
public record SpotifyTrackImpl(
        String id,
        String uri,
        String name,
        String artist,
        long durationMs,
        Instant timestamp
) implements SpotifyTrack {

    /**
     * Create a SpotifyTrack from API data, using URI as the ID.
     */
    public static SpotifyTrackImpl fromApi(String uri, String name, String artist, int durationMs) {
        return new SpotifyTrackImpl(uri, uri, name, artist, (long) durationMs, Instant.now());
    }

    /**
     * Create a SpotifyTrack with explicit timestamp.
     */
    public static SpotifyTrackImpl fromApi(String uri, String name, String artist, int durationMs, Instant timestamp) {
        return new SpotifyTrackImpl(uri, uri, name, artist, (long) durationMs, timestamp);
    }

    @Override
    @NonNull
    public String getId() {
        return id;
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public String getArtist() {
        return artist;
    }

    @Override
    public long getDurationMs() {
        return durationMs;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public String title() {
        return name;
    }

    @Override
    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    @NonNull
    public String getDescription() {
        return name + " by " + artist;
    }
}
