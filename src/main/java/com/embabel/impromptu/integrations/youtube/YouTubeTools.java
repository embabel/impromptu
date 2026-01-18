package com.embabel.impromptu.integrations.youtube;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM tools for YouTube integration.
 * Allows the chatbot to search for and play YouTube videos.
 */
@MatryoshkaTools(
        name = "youtube",
        description = "Access YouTube to play or search for videos"
)
public record YouTubeTools(
        ImpromptuUser user,
        YouTubeService youTubeService,
        YouTubePendingPlayback pendingPlayback
) {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeTools.class);

    /**
     * Check if YouTube is available.
     */
    @LlmTool(description = "Check if YouTube integration is available")
    public String checkYouTubeStatus() {
        if (!youTubeService.isConfigured()) {
            return "YouTube integration is not configured on this server.";
        }
        return "YouTube is available! You can search for and play videos.";
    }

    /**
     * Search for videos on YouTube.
     */
    @LlmTool(description = "Search for videos on YouTube (e.g., 'Brahms Symphony No. 4 Karajan')")
    public String searchVideos(String query) {
        if (!youTubeService.isConfigured()) {
            return "YouTube integration is not configured.";
        }

        try {
            var videos = youTubeService.searchVideosDetailed(query, 10);
            if (videos.isEmpty()) {
                return "No videos found for: " + query;
            }

            var sb = new StringBuilder("Found YouTube videos:\n\n");
            for (int i = 0; i < Math.min(5, videos.size()); i++) {
                var video = videos.get(i);
                sb.append(i + 1).append(". **").append(video.title()).append("**\n");
                sb.append("   Channel: ").append(video.channelTitle()).append("\n");
            }
            return sb.toString();
        } catch (YouTubeException e) {
            logger.error("Failed to search YouTube", e);
            return "Failed to search YouTube: " + e.getMessage();
        }
    }

    /**
     * Play a video on YouTube by searching for it.
     */
    @LlmTool(description = "Play a video on YouTube by searching for it (e.g., 'Brahms Violin Sonata No. 1 Perlman'). The video will appear in the YouTube player panel.")
    public String playVideo(String query) {
        if (!youTubeService.isConfigured()) {
            return "YouTube integration is not configured.";
        }

        try {
            // Search with scoring for classical music
            var videos = youTubeService.searchVideosDetailed(query, 15);
            if (videos.isEmpty()) {
                return "No videos found for: " + query;
            }

            // Best match is first (already sorted by score)
            var bestMatch = videos.getFirst();

            logger.info("Best YouTube match for '{}': {} (score: {})",
                    query, bestMatch.title(), bestMatch.score());

            // Request playback via shared cache
            pendingPlayback.requestPlayback(user.getId(), bestMatch.videoId(), bestMatch.title(), bestMatch.channelTitle());

            logger.info("Requested YouTube playback: {} - {}",
                    bestMatch.videoId(), bestMatch.title());

            return "Now playing on YouTube: **" + bestMatch.title() + "**\n" +
                    "Channel: " + bestMatch.channelTitle() +
                    (bestMatch.duration() != null ? "\nDuration: " + bestMatch.duration() : "");
        } catch (YouTubeException e) {
            logger.error("Failed to play YouTube video", e);
            return "Failed to play video: " + e.getMessage();
        }
    }

    /**
     * Play a specific video by ID.
     */
    @LlmTool(description = "Play a specific YouTube video by its video ID")
    public String playVideoById(String videoId) {
        if (!youTubeService.isConfigured()) {
            return "YouTube integration is not configured.";
        }

        try {
            var video = youTubeService.getVideoDetails(videoId);
            if (video == null) {
                return "Video not found: " + videoId;
            }

            pendingPlayback.requestPlayback(user.getId(), video.videoId(), video.title(), video.channelTitle());

            logger.info("Requested YouTube playback by ID: {} - {}",
                    video.videoId(), video.title());

            return "Now playing on YouTube: **" + video.title() + "**\n" +
                    "Channel: " + video.channelTitle() +
                    (video.duration() != null ? "\nDuration: " + video.duration() : "");
        } catch (YouTubeException e) {
            logger.error("Failed to play YouTube video", e);
            return "Failed to play video: " + e.getMessage();
        }
    }
}
