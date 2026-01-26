package com.embabel.impromptu.integrations.youtube;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.rag.model.Relationship;
import com.embabel.impromptu.integrations.Performance;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A performance on YouTube, typically a single video containing the entire work.
 * Can be persisted as a NamedEntity with relationships to videos.
 */
public interface YouTubePerformance extends Performance<YouTubeVideo> {

    /**
     * YouTube video ID (for the primary video).
     */
    String getVideoId();

    @Override
    String performer();

    @Override
    String ensemble();

    @Override
    String conductor();

    /**
     * The videos that make up this performance, stored as a relationship in Neo4j.
     * For YouTube, this is typically a single video.
     */
    @Override
    @Relationship(name = "HAS_VIDEO")
    List<YouTubeVideo> tracks();

    /**
     * Channel title serves as the album name for YouTube.
     */
    @Override
    default String albumName() {
        var videos = tracks();
        return videos.isEmpty() ? "" : videos.getFirst().getChannelTitle();
    }

    /**
     * Build a display title including work name and performers.
     */
    @Override
    default String title() {
        var sb = new StringBuilder();

        // Include work name if available
        String work = workName();
        if (work != null && !work.isBlank()) {
            sb.append(work);
        }

        // Build performer string
        StringBuilder performers = new StringBuilder();
        String p = performer();
        String e = ensemble();
        String c = conductor();
        if (p != null && !p.isBlank()) {
            performers.append(p);
        }
        if (e != null && !e.isBlank()) {
            if (!performers.isEmpty()) performers.append(" / ");
            performers.append(e);
        }
        if (c != null && !c.isBlank()) {
            if (!performers.isEmpty()) performers.append(" / ");
            performers.append(c);
        }

        // Combine work and performers
        if (!performers.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(performers);
        }

        // Fall back to video title if nothing else
        if (sb.isEmpty()) {
            var videos = tracks();
            return videos.isEmpty() ? "" : videos.getFirst().getTitle();
        }
        return sb.toString();
    }

    /**
     * Custom reference for better display in assets panel.
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
                "YouTube performance. Use the play tool to start playback. URL: " + url()
        );
    }

    @Override
    default String url() {
        return "https://www.youtube.com/watch?v=" + getVideoId();
    }

    @Override
    default String source() {
        return "youtube";
    }
}
