/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu.integrations.youtube;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    private static final int TIMEOUT_MS = 10_000; // 10 second timeout

    private final RestClient restClient;

    public YouTubeService() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

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
                + "&videoEmbeddable=true"
                + "&maxResults=" + maxResults
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&key=" + apiKey;

        Map<String, Object> response = restClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (response == null || !response.containsKey("items")) {
            return List.of();
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

        return items.stream()
                .<YouTubeVideo>map(item -> {
                    Map<String, Object> id = (Map<String, Object>) item.get("id");
                    Map<String, Object> snippet = (Map<String, Object>) item.get("snippet");
                    Map<String, Object> thumbnails = (Map<String, Object>) snippet.get("thumbnails");
                    Map<String, Object> defaultThumb = thumbnails != null
                            ? (Map<String, Object>) thumbnails.get("medium")
                            : null;

                    return YouTubeVideoImpl.fromApi(
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
     * Fetches durations via a follow-up API call.
     * Returns videos sorted by match score (best first).
     */
    public List<YouTubeVideo> searchVideosDetailed(String query, int maxResults) {
        var videos = searchVideos(query, maxResults);
        if (videos.isEmpty()) {
            return List.of();
        }

        // Fetch durations for all videos in one API call
        var durations = fetchVideoDurations(videos.stream().map(YouTubeVideo::getVideoId).toList());

        // Score and sort videos
        record ScoredVideo(YouTubeVideo video, int score) {}
        return videos.stream()
                .map(v -> {
                    int duration = durations.getOrDefault(v.getVideoId(), 0);
                    // Create a new video with duration if needed
                    YouTubeVideo videoWithDuration = duration > 0
                            ? YouTubeVideoImpl.fromApi(v.getVideoId(), v.getTitle(), v.getChannelTitle(),
                                    v.getVideoDescription(), v.getThumbnailUrl(), duration, v.getTimestamp())
                            : v;
                    return new ScoredVideo(videoWithDuration, scoreMatch(v, query));
                })
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(ScoredVideo::video)
                .toList();
    }

    /**
     * Fetch durations for multiple videos in a single API call.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Integer> fetchVideoDurations(List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }

        String ids = String.join(",", videoIds);
        String url = YOUTUBE_API_BASE + "/videos"
                + "?part=contentDetails"
                + "&id=" + ids
                + "&key=" + apiKey;

        try {
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || !response.containsKey("items")) {
                return Map.of();
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            var durations = new java.util.HashMap<String, Integer>();

            for (Map<String, Object> item : items) {
                String videoId = (String) item.get("id");
                Map<String, Object> contentDetails = (Map<String, Object>) item.get("contentDetails");
                if (contentDetails != null) {
                    String isoDuration = (String) contentDetails.get("duration");
                    durations.put(videoId, parseDurationToSeconds(isoDuration));
                }
            }

            return durations;
        } catch (Exception e) {
            logger.warn("Failed to fetch video durations: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Score how well a video matches the query.
     */
    private int scoreMatch(YouTubeVideo video, String query) {
        String queryLower = query.toLowerCase();
        String[] terms = queryLower.split("\\s+");

        String description = video.getVideoDescription() != null ? video.getVideoDescription() : "";
        String searchable = (video.getTitle() + " " + video.getChannelTitle() + " " + description).toLowerCase();

        int score = 0;
        for (String term : terms) {
            if (term.length() < 3) continue;
            if (searchable.contains(term)) {
                score += 10;
                // Bonus for title match
                if (video.getTitle().toLowerCase().contains(term)) {
                    score += 15;
                }
            }
        }

        // Bonus for classical music indicators
        String[] classicalTerms = {"symphony", "sonata", "concerto", "quartet", "opus", "op.",
                "no.", "major", "minor", "orchestra", "philharmonic", "chamber"};
        for (String term : classicalTerms) {
            if (video.getTitle().toLowerCase().contains(term)) {
                score += 5;
            }
        }

        // Bonus for known performers/orchestras in title
        String[] performers = {"berliner", "vienna", "london symphony", "chicago symphony",
                "perlman", "heifetz", "oistrakh", "menuhin", "horowitz", "richter", "rubinstein",
                "karajan", "bernstein", "abbado", "rattle", "dudamel"};
        for (String performer : performers) {
            if (video.getTitle().toLowerCase().contains(performer)) {
                score += 20;
            }
        }

        // Penalty for likely non-performance content
        String titleLower = video.getTitle().toLowerCase();
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
    public YouTubeVideo getVideoDetails(String videoId) {
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
                .body(new ParameterizedTypeReference<>() {
                });

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

        String isoDuration = contentDetails != null ? (String) contentDetails.get("duration") : null;

        return YouTubeVideoImpl.fromApi(
                videoId,
                (String) snippet.get("title"),
                (String) snippet.get("channelTitle"),
                (String) snippet.get("description"),
                defaultThumb != null ? (String) defaultThumb.get("url") : null,
                parseDurationToSeconds(isoDuration),
                Instant.now()
        );
    }

    /**
     * Parse ISO 8601 duration (PT1H2M3S) to seconds.
     */
    private int parseDurationToSeconds(String isoDuration) {
        if (isoDuration == null) return 0;

        var pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
        var matcher = pattern.matcher(isoDuration);

        if (!matcher.matches()) return 0;

        int hours = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
        int minutes = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int seconds = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        return hours * 3600 + minutes * 60 + seconds;
    }

    /**
     * Format seconds as human-readable duration string.
     */
    public static String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) return null;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

}
