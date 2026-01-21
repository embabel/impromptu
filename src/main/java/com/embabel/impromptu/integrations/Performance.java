package com.embabel.impromptu.integrations;

import java.util.List;

/**
 * A performance of a classical work, potentially spanning multiple tracks/movements.
 * <p>
 * For example, a recording of Glazunov's Violin Concerto by Heifetz with the
 * Chicago Symphony under Reiner would be a single Performance containing
 * 3 tracks (one per movement).
 * <p>
 * On YouTube, a performance is often a single video. On Spotify, it's typically
 * multiple tracks from the same album.
 */
public interface Performance extends Playable {

    /**
     * ID of the Work being performed (links to domain entity).
     */
    String workId();

    /**
     * The individual tracks/movements that make up this performance.
     * For YouTube, this is often a single video.
     * For Spotify, this is typically multiple tracks (one per movement).
     */
    List<? extends Playable> tracks();

    /**
     * Primary performer (soloist for concertos, lead ensemble for chamber music).
     */
    String performer();

    /**
     * Orchestra, quartet, or other ensemble (nullable).
     */
    String ensemble();

    /**
     * Conductor (nullable, typically for orchestral works).
     */
    String conductor();

    /**
     * Source album or video title.
     */
    String albumName();

    /**
     * Source platform (e.g., "spotify", "youtube").
     */
    String source();

    /**
     * Total duration is the sum of all track durations.
     */
    @Override
    default int durationSeconds() {
        return tracks().stream()
                .mapToInt(Playable::durationSeconds)
                .sum();
    }
}
