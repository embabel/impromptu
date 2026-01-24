package com.embabel.impromptu.integrations.youtube;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.impromptu.integrations.Performance;
import com.embabel.impromptu.integrations.Playable;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;

/**
 * A performance on YouTube, typically a single video containing the entire work.
 */
public record YouTubePerformance(
        String workId,
        String performer,
        String ensemble,
        String conductor,
        YouTubeService.YouTubeVideoDetails video
) implements Performance {

    @Override
    public @NonNull String getId() {
        return video.videoId();
    }

    @Override
    public @NonNull LlmReference reference() {
        return null;
    }

    @Override
    public @NonNull Instant getTimestamp() {
        return video.timestamp();
    }

    @Override
    public String title() {
        var sb = new StringBuilder();
        if (performer != null && !performer.isBlank()) {
            sb.append(performer);
        }
        if (ensemble != null && !ensemble.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(ensemble);
        }
        if (conductor != null && !conductor.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(conductor);
        }
        return sb.isEmpty() ? video.title() : sb.toString();
    }

    @Override
    public List<? extends Playable> tracks() {
        return List.of(video);
    }

    @Override
    public int durationSeconds() {
        return video.durationSeconds();
    }

    @Override
    public String durationFormatted() {
        return video.durationFormatted();
    }

    @Override
    public String url() {
        return video.url();
    }

    @Override
    public String albumName() {
        return video.channelTitle();
    }

    @Override
    public String source() {
        return "youtube";
    }
}
