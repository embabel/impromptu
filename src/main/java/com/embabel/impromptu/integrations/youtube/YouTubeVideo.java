package com.embabel.impromptu.integrations.youtube;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.impromptu.integrations.Playable;

/**
 * A YouTube video that can be persisted as a NamedEntity.
 * Represents a single playable video (typically the entire performance for YouTube).
 */
public interface YouTubeVideo extends NamedEntity, Playable {

    /**
     * YouTube video ID.
     */
    String getVideoId();

    /**
     * Video title.
     */
    String getTitle();

    /**
     * Channel that uploaded the video.
     */
    String getChannelTitle();

    /**
     * Video description.
     */
    String getVideoDescription();

    /**
     * URL to thumbnail image.
     */
    String getThumbnailUrl();

    /**
     * Duration in seconds. Returns long because Neo4j stores integers as Long.
     */
    long getDurationSeconds();

    @Override
    default String title() {
        return getTitle();
    }

    @Override
    default int durationSeconds() {
        return (int) getDurationSeconds();
    }

    @Override
    default String url() {
        return "https://www.youtube.com/watch?v=" + getVideoId();
    }

    @Override
    default String source() {
        return "youtube";
    }

    @Override
    default String playbackInfo() {
        return """
                {"source":"youtube","videoId":"%s","title":"%s","channelTitle":"%s","url":"%s","durationSeconds":%d}
                """.formatted(getVideoId(), getTitle(), getChannelTitle(), url(), durationSeconds()).trim();
    }
}
