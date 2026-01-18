package com.embabel.impromptu.integrations.youtube;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cache for pending YouTube playback requests, keyed by user ID.
 * Needed because the user object instance in ChatActions differs from VaadinChatView.
 */
@Component
public class YouTubePendingPlayback {

    private final ConcurrentHashMap<String, PendingVideo> pendingVideos = new ConcurrentHashMap<>();

    public record PendingVideo(String videoId, String title, String channelTitle) {
    }

    /**
     * Request playback for a user.
     */
    public void requestPlayback(String userId, String videoId, String title, String channelTitle) {
        pendingVideos.put(userId, new PendingVideo(videoId, title, channelTitle));
    }

    /**
     * Get and clear pending video for a user.
     */
    public PendingVideo consumePendingVideo(String userId) {
        return pendingVideos.remove(userId);
    }

    /**
     * Check if there's a pending video for a user.
     */
    public boolean hasPendingVideo(String userId) {
        return pendingVideos.containsKey(userId);
    }
}
