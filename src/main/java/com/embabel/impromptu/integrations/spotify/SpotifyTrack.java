package com.embabel.impromptu.integrations.spotify;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.impromptu.integrations.Playable;
import org.jspecify.annotations.NonNull;

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
