package com.embabel.impromptu.integrations;

import com.embabel.chat.Asset;

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

    @Override
    default boolean persistent() {
        return false;
    }
}
