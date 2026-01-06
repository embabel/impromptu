package com.embabel.impromptu.spotify;

import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the Spotify Web API.
 */
@Service
public class SpotifyService {

    private static final Logger logger = LoggerFactory.getLogger(SpotifyService.class);
    private static final String SPOTIFY_API_BASE = "https://api.spotify.com/v1";
    private static final String SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";

    private final RestClient restClient = RestClient.create();

    @Value("${spring.security.oauth2.client.registration.spotify.client-id:}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.spotify.client-secret:}")
    private String clientSecret;

    /**
     * Check if Spotify integration is configured.
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isEmpty()
                && clientSecret != null && !clientSecret.isEmpty();
    }

    /**
     * Check if user has linked their Spotify account.
     */
    public boolean isLinked(ImpromptuUser user) {
        return user != null && user.isSpotifyLinked();
    }

    private String basicAuthHeader() {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Exchange authorization code for access and refresh tokens.
     */
    public SpotifyTokenResponse exchangeCodeForTokens(String code, String redirectUri) {
        String formBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        Map<String, Object> response = restClient.post()
                .uri(SPOTIFY_TOKEN_URL)
                .header("Authorization", basicAuthHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            throw new SpotifyException("Empty response from Spotify token endpoint");
        }

        return new SpotifyTokenResponse(
                (String) response.get("access_token"),
                (String) response.get("refresh_token"),
                (Integer) response.get("expires_in")
        );
    }

    /**
     * Refresh the access token using the refresh token.
     */
    public SpotifyTokenResponse refreshAccessToken(String refreshToken) {
        String formBody = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

        Map<String, Object> response = restClient.post()
                .uri(SPOTIFY_TOKEN_URL)
                .header("Authorization", basicAuthHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            throw new SpotifyException("Empty response from Spotify token refresh");
        }

        return new SpotifyTokenResponse(
                (String) response.get("access_token"),
                response.containsKey("refresh_token")
                        ? (String) response.get("refresh_token")
                        : refreshToken,
                (Integer) response.get("expires_in")
        );
    }

    /**
     * Get a valid access token for the user, refreshing if necessary.
     */
    public String getValidAccessToken(ImpromptuUser user) {
        if (!isLinked(user)) {
            throw new SpotifyException("User has not linked Spotify account");
        }

        // Check if token is expired or about to expire (within 5 minutes)
        Instant expiry = user.getSpotifyTokenExpiry();
        if (expiry != null && expiry.isAfter(Instant.now().plusSeconds(300))) {
            return user.getSpotifyAccessToken();
        }

        // Refresh the token
        logger.info("Refreshing Spotify token for user: {}", user.getId());
        SpotifyTokenResponse tokens = refreshAccessToken(user.getSpotifyRefreshToken());

        user.setSpotifyAccessToken(tokens.accessToken());
        user.setSpotifyRefreshToken(tokens.refreshToken());
        user.setSpotifyTokenExpiry(Instant.now().plusSeconds(tokens.expiresIn()));

        return tokens.accessToken();
    }

    /**
     * Get the current user's Spotify profile.
     */
    public SpotifyUser getCurrentUser(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        Map<String, Object> response = restClient.get()
                .uri(SPOTIFY_API_BASE + "/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return new SpotifyUser(
                (String) response.get("id"),
                (String) response.get("display_name")
        );
    }

    /**
     * Get user's playlists.
     */
    @SuppressWarnings("unchecked")
    public List<SpotifyPlaylist> getUserPlaylists(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        Map<String, Object> response = restClient.get()
                .uri(SPOTIFY_API_BASE + "/me/playlists?limit=50")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

        return items.stream()
                .map(item -> new SpotifyPlaylist(
                        (String) item.get("id"),
                        (String) item.get("name"),
                        ((Map<String, Object>) item.get("tracks")).get("total") instanceof Integer i ? i : 0
                ))
                .toList();
    }

    /**
     * Create a new playlist.
     */
    public SpotifyPlaylist createPlaylist(ImpromptuUser user, String name, String description, boolean isPublic) {
        String token = getValidAccessToken(user);
        String spotifyUserId = user.getSpotifyUserId();

        Map<String, Object> body = Map.of(
                "name", name,
                "description", description,
                "public", isPublic
        );

        Map<String, Object> response = restClient.post()
                .uri(SPOTIFY_API_BASE + "/users/" + spotifyUserId + "/playlists")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return new SpotifyPlaylist(
                (String) response.get("id"),
                (String) response.get("name"),
                0
        );
    }

    /**
     * Add tracks to a playlist.
     */
    public void addTracksToPlaylist(ImpromptuUser user, String playlistId, List<String> trackUris) {
        String token = getValidAccessToken(user);

        Map<String, Object> body = Map.of("uris", trackUris);

        restClient.post()
                .uri(SPOTIFY_API_BASE + "/playlists/" + playlistId + "/tracks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        logger.info("Added {} tracks to playlist {}", trackUris.size(), playlistId);
    }

    /**
     * Search for tracks.
     */
    @SuppressWarnings("unchecked")
    public List<SpotifyTrack> searchTracks(ImpromptuUser user, String query, int limit) {
        String token = getValidAccessToken(user);

        String url = SPOTIFY_API_BASE + "/search?type=track&limit=" + limit + "&q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8);

        Map<String, Object> response = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        Map<String, Object> tracks = (Map<String, Object>) response.get("tracks");
        List<Map<String, Object>> items = (List<Map<String, Object>>) tracks.get("items");

        return items.stream()
                .map(item -> {
                    List<Map<String, Object>> artists = (List<Map<String, Object>>) item.get("artists");
                    String artistName = artists.isEmpty() ? "Unknown" : (String) artists.get(0).get("name");
                    return new SpotifyTrack(
                            (String) item.get("uri"),
                            (String) item.get("name"),
                            artistName
                    );
                })
                .toList();
    }

    // ========== Spotify Connect API Methods ==========

    /**
     * Get available playback devices.
     */
    @SuppressWarnings("unchecked")
    public List<SpotifyDevice> getDevices(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        Map<String, Object> response = restClient.get()
                .uri(SPOTIFY_API_BASE + "/me/player/devices")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.containsKey("devices")) {
            return List.of();
        }

        List<Map<String, Object>> devices = (List<Map<String, Object>>) response.get("devices");
        return devices.stream()
                .map(d -> new SpotifyDevice(
                        (String) d.get("id"),
                        (String) d.get("name"),
                        (String) d.get("type"),
                        Boolean.TRUE.equals(d.get("is_active")),
                        d.get("volume_percent") instanceof Integer v ? v : 0
                ))
                .toList();
    }

    /**
     * Get current playback state.
     */
    @SuppressWarnings("unchecked")
    public PlaybackState getPlaybackState(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        try {
            Map<String, Object> response = restClient.get()
                    .uri(SPOTIFY_API_BASE + "/me/player")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) {
                return PlaybackState.inactive();
            }

            boolean isPlaying = Boolean.TRUE.equals(response.get("is_playing"));
            int progressMs = response.get("progress_ms") instanceof Integer p ? p : 0;
            int volumePercent = response.get("device") instanceof Map<?, ?> device
                    ? (device.get("volume_percent") instanceof Integer v ? v : 0) : 0;

            String deviceId = null;
            String deviceName = null;
            if (response.get("device") instanceof Map<?, ?> device) {
                deviceId = (String) device.get("id");
                deviceName = (String) device.get("name");
            }

            String trackName = null;
            String artistName = null;
            String albumName = null;
            String albumImageUrl = null;
            int durationMs = 0;

            if (response.get("item") instanceof Map<?, ?> item) {
                trackName = (String) item.get("name");
                durationMs = item.get("duration_ms") instanceof Integer d ? d : 0;

                if (item.get("artists") instanceof List<?> artists && !artists.isEmpty()) {
                    if (artists.get(0) instanceof Map<?, ?> artist) {
                        artistName = (String) artist.get("name");
                    }
                }

                if (item.get("album") instanceof Map<?, ?> album) {
                    albumName = (String) album.get("name");
                    if (album.get("images") instanceof List<?> images && !images.isEmpty()) {
                        if (images.get(0) instanceof Map<?, ?> image) {
                            albumImageUrl = (String) image.get("url");
                        }
                    }
                }
            }

            return new PlaybackState(
                    true, isPlaying, deviceId, deviceName,
                    trackName, artistName, albumName, albumImageUrl,
                    progressMs, durationMs, volumePercent
            );
        } catch (Exception e) {
            logger.debug("No active playback: {}", e.getMessage());
            return PlaybackState.inactive();
        }
    }

    /**
     * Transfer playback to a specific device.
     */
    public void transferPlayback(ImpromptuUser user, String deviceId, boolean play) {
        String token = getValidAccessToken(user);

        Map<String, Object> body = Map.of(
                "device_ids", List.of(deviceId),
                "play", play
        );

        restClient.put()
                .uri(SPOTIFY_API_BASE + "/me/player")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        logger.info("Transferred playback to device: {}", deviceId);
    }

    /**
     * Start or resume playback.
     */
    public void play(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        restClient.put()
                .uri(SPOTIFY_API_BASE + "/me/player/play")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Start playback of a specific track or context.
     */
    public void play(ImpromptuUser user, String contextUri, List<String> trackUris) {
        String token = getValidAccessToken(user);

        var bodyBuilder = new java.util.HashMap<String, Object>();
        if (contextUri != null) {
            bodyBuilder.put("context_uri", contextUri);
        }
        if (trackUris != null && !trackUris.isEmpty()) {
            bodyBuilder.put("uris", trackUris);
        }

        restClient.put()
                .uri(SPOTIFY_API_BASE + "/me/player/play")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyBuilder)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Pause playback.
     */
    public void pause(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        restClient.put()
                .uri(SPOTIFY_API_BASE + "/me/player/pause")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Skip to next track.
     */
    public void skipToNext(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        restClient.post()
                .uri(SPOTIFY_API_BASE + "/me/player/next")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Skip to previous track.
     */
    public void skipToPrevious(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        restClient.post()
                .uri(SPOTIFY_API_BASE + "/me/player/previous")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Set playback volume.
     */
    public void setVolume(ImpromptuUser user, int volumePercent) {
        String token = getValidAccessToken(user);
        int volume = Math.max(0, Math.min(100, volumePercent));

        restClient.put()
                .uri(SPOTIFY_API_BASE + "/me/player/volume?volume_percent=" + volume)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    // ========== Record types for API responses ==========

    public record SpotifyTokenResponse(String accessToken, String refreshToken, int expiresIn) {}
    public record SpotifyUser(String id, String displayName) {}
    public record SpotifyPlaylist(String id, String name, int trackCount) {}
    public record SpotifyTrack(String uri, String name, String artist) {}

    public record SpotifyDevice(String id, String name, String type, boolean isActive, int volumePercent) {}

    public record PlaybackState(
            boolean isActive,
            boolean isPlaying,
            String deviceId,
            String deviceName,
            String trackName,
            String artistName,
            String albumName,
            String albumImageUrl,
            int progressMs,
            int durationMs,
            int volumePercent
    ) {
        public static PlaybackState inactive() {
            return new PlaybackState(false, false, null, null, null, null, null, null, 0, 0, 0);
        }
    }
}
