package com.embabel.impromptu.integrations.youtube;

import com.embabel.impromptu.integrations.Performance;
import com.embabel.impromptu.integrations.Playable;

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
    public String id() {
        return video.videoId();
    }

    @Override
    public String title() {
        StringBuilder sb = new StringBuilder();
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
