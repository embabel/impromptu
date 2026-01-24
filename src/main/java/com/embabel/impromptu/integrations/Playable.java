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
    String durationFormatted();

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
     * Default reference implementation providing a play tool.
     */
    @Override
    @NonNull
    default LlmReference reference() {
        return LlmReference.of(
                title(),
                "Playable media: " + title() + " (" + durationFormatted() + ")",
                List.of(playTool()),
                "Use the play tool to start playback. URL: " + url()
        );
    }
}
