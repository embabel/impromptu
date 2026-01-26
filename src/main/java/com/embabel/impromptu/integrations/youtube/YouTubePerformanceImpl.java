package com.embabel.impromptu.integrations.youtube;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of YouTubePerformance for creating instances from YouTube API responses.
 */
public record YouTubePerformanceImpl(
        String id,
        String workId,
        String workName,
        String videoId,
        String performer,
        String ensemble,
        String conductor,
        List<YouTubeVideo> videos,
        Instant timestamp
) implements YouTubePerformance {

    /**
     * Create a YouTubePerformance from a single video with work name.
     */
    public static YouTubePerformanceImpl create(
            String workId,
            String workName,
            String performer,
            String ensemble,
            String conductor,
            YouTubeVideo video
    ) {
        return new YouTubePerformanceImpl(
                video.getVideoId(),
                workId,
                workName,
                video.getVideoId(),
                performer,
                ensemble,
                conductor,
                List.of(video),
                video.getTimestamp()
        );
    }

    /**
     * Create a YouTubePerformance from a single video (legacy, no work name).
     */
    public static YouTubePerformanceImpl create(
            String workId,
            String performer,
            String ensemble,
            String conductor,
            YouTubeVideo video
    ) {
        return create(workId, null, performer, ensemble, conductor, video);
    }

    /**
     * Create a YouTubePerformance from multiple videos.
     */
    public static YouTubePerformanceImpl create(
            String workId,
            String workName,
            String performer,
            String ensemble,
            String conductor,
            List<YouTubeVideo> videos
    ) {
        String videoId = videos.isEmpty() ? "unknown" : videos.getFirst().getVideoId();
        Instant timestamp = videos.isEmpty() ? Instant.now() : videos.getFirst().getTimestamp();
        return new YouTubePerformanceImpl(videoId, workId, workName, videoId, performer, ensemble, conductor, videos, timestamp);
    }

    /**
     * Create a YouTubePerformance from multiple videos (legacy, no work name).
     */
    public static YouTubePerformanceImpl create(
            String workId,
            String performer,
            String ensemble,
            String conductor,
            List<YouTubeVideo> videos
    ) {
        return create(workId, null, performer, ensemble, conductor, videos);
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
    public String performer() {
        return performer;
    }

    @Override
    public String ensemble() {
        return ensemble;
    }

    @Override
    public String conductor() {
        return conductor;
    }

    @Override
    public List<YouTubeVideo> tracks() {
        return videos;
    }

    @Override
    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String workName() {
        return workName;
    }

    @Override
    @NonNull
    public String getName() {
        return title();
    }

    @Override
    @NonNull
    public String getDescription() {
        return title();
    }

    @Override
    public String playbackInfo() {
        var video = videos.isEmpty() ? null : videos.getFirst();
        return """
                {"source":"youtube","videoId":"%s","title":"%s","performer":"%s","ensemble":"%s","conductor":"%s","channelTitle":"%s","url":"%s","durationSeconds":%d}
                """.formatted(
                videoId,
                video != null ? video.getTitle() : "",
                performer != null ? performer : "",
                ensemble != null ? ensemble : "",
                conductor != null ? conductor : "",
                video != null ? video.getChannelTitle() : "",
                url(),
                durationSeconds()
        ).trim();
    }
}
