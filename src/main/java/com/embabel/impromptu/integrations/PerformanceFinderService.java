package com.embabel.impromptu.integrations;

import com.embabel.agent.api.tool.AgenticTool;
import com.embabel.agent.api.tool.Tool;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.user.ImpromptuUser;
import org.springframework.stereotype.Service;

/**
 * Service for finding performances of classical works using an LLM-orchestrated search.
 * <p>
 * Uses an {@link AgenticTool} that orchestrates searches across Spotify and YouTube,
 * using the LLM to:
 * <ul>
 *   <li>Construct appropriate search queries for the work</li>
 *   <li>Parse performer/conductor/ensemble from track metadata</li>
 *   <li>Group tracks into coherent performances</li>
 *   <li>Return structured Performance objects</li>
 * </ul>
 */
@Service
public class PerformanceFinderService {

    private static final String SYSTEM_PROMPT = """
            You are a classical music expert finding performances of classical works.
            
            Given a work (composer and title), your task is to:
            1. Search for performances on Spotify and/or YouTube
            2. For each result, identify:
               - The performer (soloist for concertos, lead musician for chamber music)
               - The ensemble/orchestra (if applicable)
               - The conductor (if applicable)
            3. Group tracks that belong to the same performance (same album, same performers)
            4. Create Performance objects for each distinct performance found
            
            Tips for parsing classical music metadata:
            - The "artist" field often contains the composer, not the performer
            - Look for performer names in the track title or album name
            - Common patterns: "Work - Performer, Orchestra, Conductor"
            - Multiple tracks with sequential numbers (I., II., III. or 1., 2., 3.) are movements
            - Movements of the same work share album ID and similar track names
            
            For Spotify:
            1. First search for tracks matching the work
            2. Use getSpotifyAlbumTracks to get all tracks from promising albums
            3. Identify which tracks are movements of the work
            4. Create a SpotifyPerformance with those track URIs
            
            For YouTube:
            1. Search for videos of the work
            2. Prefer videos with full performances (longer duration)
            3. Create a YouTubePerformance for each good result
            
            Return performances from both platforms if available.
            """;

    private final SpotifyService spotifyService;
    private final YouTubeService youTubeService;

    public PerformanceFinderService(
            SpotifyService spotifyService,
            YouTubeService youTubeService) {
        this.spotifyService = spotifyService;
        this.youTubeService = youTubeService;
    }

    /**
     * Create an AgenticTool configured for finding performances.
     * This tool can be used in an agent action's tool loop.
     *
     * @param user   The user (needed for Spotify API access)
     * @param workId The ID of the Work being searched for
     * @return Configured AgenticTool
     */
    public Tool createPerformanceFinderTool(ImpromptuUser user, String workId) {
        var searchTools = new PerformanceSearchTools(spotifyService, youTubeService, user);

        return new AgenticTool(
                "findPerformances",
                "Find performances of a classical work on Spotify and YouTube. " +
                        "Returns structured performance data including performers, conductors, and track lists."
        )
                .withToolObject(searchTools)
                .withSystemPrompt(SYSTEM_PROMPT)
                .withParameter(Tool.Parameter.string(
                        "workQuery",
                        "The work to search for, e.g., 'Glazunov Violin Concerto' or 'Brahms Symphony No. 4'"
                ));
    }

    /**
     * Check if performance finding is available (at least one platform configured).
     */
    public boolean isAvailable(ImpromptuUser user) {
        boolean spotifyAvailable = spotifyService.isConfigured() && spotifyService.isLinked(user);
        boolean youtubeAvailable = youTubeService.isConfigured();
        return spotifyAvailable || youtubeAvailable;
    }

    /**
     * Get availability status message.
     */
    public String getAvailabilityStatus(ImpromptuUser user) {
        boolean spotify = spotifyService.isConfigured() && spotifyService.isLinked(user);
        boolean youtube = youTubeService.isConfigured();

        if (spotify && youtube) {
            return "Spotify and YouTube available";
        } else if (spotify) {
            return "Spotify available (YouTube not configured)";
        } else if (youtube) {
            return "YouTube available (Spotify not linked)";
        } else {
            return "No music platforms available";
        }
    }
}
