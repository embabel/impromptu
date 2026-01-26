package com.embabel.impromptu.integrations.spotify;

import com.embabel.agent.rag.model.Relationship;
import com.embabel.impromptu.integrations.Performance;

import java.util.List;

/**
 * A performance on Spotify, typically consisting of multiple tracks (movements)
 * from the same album. Can be persisted as a NamedEntity with relationships to tracks.
 */
public interface SpotifyPerformance extends Performance<SpotifyTrack> {

    /**
     * Spotify album ID.
     */
    String getAlbumId();

    @Override
    String albumName();

    @Override
    String performer();

    @Override
    String ensemble();

    @Override
    String conductor();

    /**
     * The tracks that make up this performance, stored as a relationship in Neo4j.
     */
    @Override
    @Relationship(name = "HAS_TRACK")
    List<SpotifyTrack> tracks();

    /**
     * Build a title from performer, ensemble, and conductor.
     * Falls back to album name if none are available.
     */
    @Override
    default String title() {
        StringBuilder sb = new StringBuilder();
        String p = performer();
        String e = ensemble();
        String c = conductor();
        if (p != null && !p.isBlank()) {
            sb.append(p);
        }
        if (e != null && !e.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(e);
        }
        if (c != null && !c.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(c);
        }
        return sb.isEmpty() ? albumName() : sb.toString();
    }

    @Override
    default String url() {
        return "https://open.spotify.com/album/" + getAlbumId();
    }

    @Override
    default String source() {
        return "spotify";
    }

    /**
     * Get all track URIs for playback.
     */
    default List<String> trackUris() {
        return tracks().stream()
                .map(SpotifyTrack::getUri)
                .toList();
    }
}
