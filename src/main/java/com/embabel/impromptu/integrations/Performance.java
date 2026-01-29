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
package com.embabel.impromptu.integrations;

import com.embabel.agent.rag.model.NamedEntity;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A performance of a single classical work, potentially spanning multiple tracks/movements.
 * <p>
 * For example, a recording of Glazunov's Violin Concerto by Heifetz with the
 * Chicago Symphony under Reiner would be a single Performance containing
 * 3 tracks (one per movement).
 * <p>
 * On YouTube, a performance is often a single video. On Spotify, it's typically
 * multiple tracks from the same album.
 *
 * @param <T> the type of tracks in this performance
 */
public interface Performance<T extends Playable> extends Playable, NamedEntity {

    /**
     * ID of the Work being performed (links to domain entity).
     */
    String workId();

    /**
     * Name/title of the work being performed.
     * Override to provide the actual work name for display.
     */
    default String workName() {
        return null;
    }

    @Override
    default @NonNull String getName() {
        return title();
    }

    /**
     * The individual tracks/movements that make up this performance.
     * For YouTube, this is often a single video.
     * For Spotify, this is typically multiple tracks (one per movement).
     */
    List<T> tracks();

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
