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

    // Record types for API responses

    public record SpotifyTokenResponse(String accessToken, String refreshToken, int expiresIn) {}
    public record SpotifyUser(String id, String displayName) {}
    public record SpotifyPlaylist(String id, String name, int trackCount) {}
    public record SpotifyTrack(String uri, String name, String artist) {}
}
