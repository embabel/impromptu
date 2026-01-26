package com.embabel.impromptu.integrations.spotify;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.rag.model.Relationship;
import com.embabel.impromptu.integrations.Performance;
import org.jspecify.annotations.NonNull;

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
     * Build a display title including work name and performers.
     */
    @Override
    default String title() {
        StringBuilder sb = new StringBuilder();

        // Include work name if available
        String work = workName();
        if (work != null && !work.isBlank()) {
            sb.append(work);
        }

        // Build performer string
        StringBuilder performers = new StringBuilder();
        String p = performer();
        String e = ensemble();
        String c = conductor();
        if (p != null && !p.isBlank()) {
            performers.append(p);
        }
        if (e != null && !e.isBlank()) {
            if (!performers.isEmpty()) performers.append(" / ");
            performers.append(e);
        }
        if (c != null && !c.isBlank()) {
            if (!performers.isEmpty()) performers.append(" / ");
            performers.append(c);
        }

        // Combine work and performers
        if (!performers.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(performers);
        }

        return sb.isEmpty() ? albumName() : sb.toString();
    }

    /**
     * Custom reference for better display in assets panel.
     */
    @Override
    @NonNull
    default LlmReference reference() {
        String duration = durationFormatted();
        String durationPart = duration != null ? " [" + duration + "]" : "";
        String trackCount = tracks().size() > 1 ? " (" + tracks().size() + " tracks)" : "";

        return LlmReference.of(
                shortId(),
                title() + durationPart + trackCount,
                List.of(playTool()),
                "Spotify performance. Use the play tool to start playback. URL: " + url()
        );
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
