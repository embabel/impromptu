package com.embabel.impromptu.youtube;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for searching YouTube videos using the YouTube Data API v3.
 * Uses RestClient for direct API calls (no Google client library dependency).
 */
@Service
public class YouTubeService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeService.class);
    private static final String YOUTUBE_API_BASE = "https://www.googleapis.com/youtube/v3";

    private final RestClient restClient = RestClient.create();

    @Value("${youtube.api-key:}")
    private String apiKey;

    @PostConstruct
    void init() {
        if (!isConfigured()) {
            logger.warn("YouTube API key not configured. Set YOUTUBE_API_KEY environment variable to enable YouTube integration.");
        }
    }

    /**
     * Check if YouTube API is configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Search for videos.
     */
    @SuppressWarnings("unchecked")
    public List<YouTubeVideo> searchVideos(String query, int maxResults) {
        if (!isConfigured()) {
            throw new YouTubeException("YouTube API key not configured");
        }

        String url = YOUTUBE_API_BASE + "/search"
                + "?part=snippet"
                + "&type=video"
                + "&maxResults=" + maxResults
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&key=" + apiKey;

        Map<String, Object> response = restClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.containsKey("items")) {
            return List.of();
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

        return items.stream()
                .map(item -> {
                    Map<String, Object> id = (Map<String, Object>) item.get("id");
                    Map<String, Object> snippet = (Map<String, Object>) item.get("snippet");
                    Map<String, Object> thumbnails = (Map<String, Object>) snippet.get("thumbnails");
                    Map<String, Object> defaultThumb = thumbnails != null
                            ? (Map<String, Object>) thumbnails.get("medium")
                            : null;

                    return new YouTubeVideo(
                            (String) id.get("videoId"),
                            (String) snippet.get("title"),
                            (String) snippet.get("channelTitle"),
                            (String) snippet.get("description"),
                            defaultThumb != null ? (String) defaultThumb.get("url") : null
                    );
                })
                .toList();
    }

    /**
     * Search with detailed scoring for classical music.
     */
    public List<YouTubeVideoDetails> searchVideosDetailed(String query, int maxResults) {
        var videos = searchVideos(query, maxResults);
        return videos.stream()
                .map(v -> new YouTubeVideoDetails(
                        v.videoId(),
                        v.title(),
                        v.channelTitle(),
                        v.description(),
                        v.thumbnailUrl(),
                        scoreMatch(v, query)
                ))
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .toList();
    }

    /**
     * Score how well a video matches the query.
     */
    private int scoreMatch(YouTubeVideo video, String query) {
        String queryLower = query.toLowerCase();
        String[] terms = queryLower.split("\\s+");

        String searchable = (video.title() + " " + video.channelTitle() + " " + video.description()).toLowerCase();

        int score = 0;
        for (String term : terms) {
            if (term.length() < 3) continue;
            if (searchable.contains(term)) {
                score += 10;
                // Bonus for title match
                if (video.title().toLowerCase().contains(term)) {
                    score += 15;
                }
            }
        }

        // Bonus for classical music indicators
        String[] classicalTerms = {"symphony", "sonata", "concerto", "quartet", "opus", "op.",
                "no.", "major", "minor", "orchestra", "philharmonic", "chamber"};
        for (String term : classicalTerms) {
            if (video.title().toLowerCase().contains(term)) {
                score += 5;
            }
        }

        // Bonus for known performers/orchestras in title
        String[] performers = {"berliner", "vienna", "london symphony", "chicago symphony",
                "perlman", "heifetz", "oistrakh", "menuhin", "horowitz", "richter", "rubinstein",
                "karajan", "bernstein", "abbado", "rattle", "dudamel"};
        for (String performer : performers) {
            if (video.title().toLowerCase().contains(performer)) {
                score += 20;
            }
        }

        // Penalty for likely non-performance content
        String titleLower = video.title().toLowerCase();
        if (titleLower.contains("tutorial") || titleLower.contains("lesson") ||
            titleLower.contains("how to") || titleLower.contains("learn")) {
            score -= 30;
        }

        return score;
    }

    /**
     * Get video details by ID (for duration, etc.).
     */
    @SuppressWarnings("unchecked")
    public YouTubeVideoDetails getVideoDetails(String videoId) {
        if (!isConfigured()) {
            throw new YouTubeException("YouTube API key not configured");
        }

        String url = YOUTUBE_API_BASE + "/videos"
                + "?part=snippet,contentDetails"
                + "&id=" + videoId
                + "&key=" + apiKey;

        Map<String, Object> response = restClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.containsKey("items")) {
            return null;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        if (items.isEmpty()) {
            return null;
        }

        Map<String, Object> item = items.get(0);
        Map<String, Object> snippet = (Map<String, Object>) item.get("snippet");
        Map<String, Object> contentDetails = (Map<String, Object>) item.get("contentDetails");
        Map<String, Object> thumbnails = (Map<String, Object>) snippet.get("thumbnails");
        Map<String, Object> defaultThumb = thumbnails != null
                ? (Map<String, Object>) thumbnails.get("medium")
                : null;

        String duration = contentDetails != null ? (String) contentDetails.get("duration") : null;

        return new YouTubeVideoDetails(
                videoId,
                (String) snippet.get("title"),
                (String) snippet.get("channelTitle"),
                (String) snippet.get("description"),
                defaultThumb != null ? (String) defaultThumb.get("url") : null,
                0,
                parseDuration(duration)
        );
    }

    /**
     * Parse ISO 8601 duration (PT1H2M3S) to human readable.
     */
    private String parseDuration(String isoDuration) {
        if (isoDuration == null) return null;

        var pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
        var matcher = pattern.matcher(isoDuration);

        if (!matcher.matches()) return isoDuration;

        int hours = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
        int minutes = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int seconds = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    // ========== Record types ==========

    public record YouTubeVideo(
            String videoId,
            String title,
            String channelTitle,
            String description,
            String thumbnailUrl
    ) {}

    public record YouTubeVideoDetails(
            String videoId,
            String title,
            String channelTitle,
            String description,
            String thumbnailUrl,
            int score,
            String duration
    ) {
        public YouTubeVideoDetails(String videoId, String title, String channelTitle,
                                   String description, String thumbnailUrl, int score) {
            this(videoId, title, channelTitle, description, thumbnailUrl, score, null);
        }

        public String displayString() {
            String durationStr = duration != null ? " [" + duration + "]" : "";
            return title + " - " + channelTitle + durationStr;
        }
    }
}
