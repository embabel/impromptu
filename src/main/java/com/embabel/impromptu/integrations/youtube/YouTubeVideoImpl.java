package com.embabel.impromptu.integrations.youtube;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

/**
 * Implementation of YouTubeVideo for creating instances from YouTube API responses.
 */
public record YouTubeVideoImpl(
        String id,
        String videoId,
        String title,
        String channelTitle,
        String videoDescription,
        String thumbnailUrl,
        long durationSecondsValue,
        Instant timestamp
) implements YouTubeVideo {

    /**
     * Create a YouTubeVideo from API data, using videoId as the ID.
     */
    public static YouTubeVideoImpl fromApi(String videoId, String title, String channelTitle,
                                           String description, String thumbnailUrl) {
        return new YouTubeVideoImpl(videoId, videoId, title, channelTitle, description, thumbnailUrl, 0L, Instant.now());
    }

    /**
     * Create a YouTubeVideo with duration.
     */
    public static YouTubeVideoImpl fromApi(String videoId, String title, String channelTitle,
                                           String description, String thumbnailUrl, int durationSeconds) {
        return new YouTubeVideoImpl(videoId, videoId, title, channelTitle, description, thumbnailUrl,
                (long) durationSeconds, Instant.now());
    }

    /**
     * Create a YouTubeVideo with all fields.
     */
    public static YouTubeVideoImpl fromApi(String videoId, String title, String channelTitle,
                                           String description, String thumbnailUrl, int durationSeconds,
                                           Instant timestamp) {
        return new YouTubeVideoImpl(videoId, videoId, title, channelTitle, description, thumbnailUrl,
                (long) durationSeconds, timestamp);
    }

    @Override
    @NonNull
    public String getId() {
        return id;
    }

    @Override
    public String getVideoId() {
        return videoId;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getChannelTitle() {
        return channelTitle;
    }

    @Override
    public String getVideoDescription() {
        return videoDescription;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @Override
    public long getDurationSeconds() {
        return durationSecondsValue;
    }

    @Override
    public int durationSeconds() {
        return (int) durationSecondsValue;
    }

    @Override
    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    @NonNull
    public String getName() {
        return title;
    }

    @Override
    @NonNull
    public String getDescription() {
        return title + " - " + channelTitle;
    }

    /**
     * Display string for logging/debugging.
     */
    public String displayString() {
        String durationStr = durationSecondsValue > 0 ? " [" + durationFormatted() + "]" : "";
        return title + " - " + channelTitle + durationStr;
    }
}
