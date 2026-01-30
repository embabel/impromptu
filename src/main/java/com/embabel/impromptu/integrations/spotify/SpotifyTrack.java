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

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.impromptu.domain.performance.Playable;

/**
 * A track from Spotify that can be persisted as a NamedEntity.
 * Represents a single playable item (typically a movement in a classical performance).
 */
public interface SpotifyTrack extends NamedEntity, Playable {

    /**
     * Spotify URI (e.g., "spotify:track:xxx").
     */
    String getUri();

    /**
     * Artist name.
     */
    String getArtist();

    /**
     * Duration in milliseconds. Returns long because Neo4j stores integers as Long.
     */
    long getDurationMs();

    /**
     * Track title for display. Returns the track name.
     */
    @Override
    default String title() {
        return getName();
    }

    @Override
    default int durationSeconds() {
        return (int) (getDurationMs() / 1000);
    }

    @Override
    default String url() {
        String uri = getUri();
        if (uri != null && uri.startsWith("spotify:track:")) {
            return "https://open.spotify.com/track/" + uri.substring(14);
        }
        return null;
    }

    @Override
    default String source() {
        return "spotify";
    }

    @Override
    default String playbackInfo() {
        return """
                {"source":"spotify","uri":"%s","id":"%s","title":"%s","artist":"%s","url":"%s","durationSeconds":%d}
                """.formatted(getUri(), getId(), title(), getArtist(), url(), durationSeconds()).trim();
    }
}
