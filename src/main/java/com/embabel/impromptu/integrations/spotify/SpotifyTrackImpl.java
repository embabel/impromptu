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
