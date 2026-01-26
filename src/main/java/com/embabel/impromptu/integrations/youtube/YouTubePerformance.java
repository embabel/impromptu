package com.embabel.impromptu.integrations.youtube;

import com.embabel.agent.rag.model.Relationship;
import com.embabel.impromptu.integrations.Performance;

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
     * Build a title from performer, ensemble, and conductor.
     * Falls back to video title if none are available.
     */
    @Override
    default String title() {
        StringBuilder sb = new StringBuilder();
        String p = performer();
        String e = ensemble();
        String c = conductor();
        if (p != null && !p.isBlank()) {
            sb.append(p);
        }
        if (e != null && !e.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(e);
        }
        if (c != null && !c.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(c);
        }
        if (sb.isEmpty()) {
            var videos = tracks();
            return videos.isEmpty() ? "" : videos.getFirst().getTitle();
        }
        return sb.toString();
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
