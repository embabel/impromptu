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

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.chat.Asset;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Common interface for playable media items (Spotify tracks, YouTube videos, etc.).
 */
public interface Playable extends Asset {

    /**
     * Display title of the media item.
     */
    String title();

    /**
     * Duration in seconds.
     */
    int durationSeconds();

    /**
     * Human-readable duration string (e.g., "3:45" or "1:23:45").
     */
    default String durationFormatted() {
        int total = durationSeconds();
        if (total <= 0) return null;
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * URL to play or view the media item.
     */
    String url();

    /**
     * Source platform identifier (e.g., "spotify", "youtube").
     */
    String source();

    @Override
    default boolean persistent() {
        return false;
    }

    /**
     * Create a tool to play this item.
     * Subclasses should override to provide platform-specific playback info.
     */
    default Tool playTool() {
        return Tool.create(
                "play",
                "Play " + title(),
                input -> Tool.Result.text(playbackInfo())
        );
    }

    /**
     * Return JSON-formatted playback info for this item.
     * Used by the play tool to provide information needed for playback.
     */
    default String playbackInfo() {
        return """
                {"source":"%s","id":"%s","title":"%s","url":"%s","durationSeconds":%d}
                """.formatted(source(), getId(), title(), url(), durationSeconds()).trim();
    }

    /**
     * Short identifier for reference naming.
     * Creates a readable name from title rather than using opaque IDs.
     */
    default String shortId() {
        var name = title().toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (name.length() > 25) {
            name = name.substring(0, 25);
        }
        return name;
    }

    /**
     * Default reference implementation providing a play tool.
     */
    @Override
    @NonNull
    default LlmReference reference() {
        String duration = durationFormatted();
        String durationPart = duration != null ? " [" + duration + "]" : "";
        return LlmReference.of(
                shortId(),
                title() + durationPart,
                List.of(playTool()),
                source() + " - " + title() + ". URL: " + url()
        );
    }
}
