package com.embabel.impromptu.spotify;

import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();

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

    /**
     * Exchange authorization code for access and refresh tokens.
     */
    public SpotifyTokenResponse exchangeCodeForTokens(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                SPOTIFY_TOKEN_URL, request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new SpotifyException("Empty response from Spotify token endpoint");
        }

        return new SpotifyTokenResponse(
                (String) responseBody.get("access_token"),
                (String) responseBody.get("refresh_token"),
                (Integer) responseBody.get("expires_in")
        );
    }

    /**
     * Refresh the access token using the refresh token.
     */
    public SpotifyTokenResponse refreshAccessToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                SPOTIFY_TOKEN_URL, request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new SpotifyException("Empty response from Spotify token refresh");
        }

        return new SpotifyTokenResponse(
                (String) responseBody.get("access_token"),
                // Refresh token may not be returned, keep the old one
                responseBody.containsKey("refresh_token")
                        ? (String) responseBody.get("refresh_token")
                        : refreshToken,
                (Integer) responseBody.get("expires_in")
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

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                SPOTIFY_API_BASE + "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        Map<String, Object> body = response.getBody();
        return new SpotifyUser(
                (String) body.get("id"),
                (String) body.get("display_name")
        );
    }

    /**
     * Get user's playlists.
     */
    @SuppressWarnings("unchecked")
    public List<SpotifyPlaylist> getUserPlaylists(ImpromptuUser user) {
        String token = getValidAccessToken(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                SPOTIFY_API_BASE + "/me/playlists?limit=50",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

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

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", name,
                "description", description,
                "public", isPublic
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                SPOTIFY_API_BASE + "/users/" + spotifyUserId + "/playlists",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        Map<String, Object> responseBody = response.getBody();
        return new SpotifyPlaylist(
                (String) responseBody.get("id"),
                (String) responseBody.get("name"),
                0
        );
    }

    /**
     * Add tracks to a playlist.
     */
    public void addTracksToPlaylist(ImpromptuUser user, String playlistId, List<String> trackUris) {
        String token = getValidAccessToken(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("uris", trackUris);

        restTemplate.exchange(
                SPOTIFY_API_BASE + "/playlists/" + playlistId + "/tracks",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        logger.info("Added {} tracks to playlist {}", trackUris.size(), playlistId);
    }

    /**
     * Search for tracks.
     */
    @SuppressWarnings("unchecked")
    public List<SpotifyTrack> searchTracks(ImpromptuUser user, String query, int limit) {
        String token = getValidAccessToken(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        String url = SPOTIFY_API_BASE + "/search?type=track&limit=" + limit + "&q=" +
                java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        Map<String, Object> body = response.getBody();
        Map<String, Object> tracks = (Map<String, Object>) body.get("tracks");
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
