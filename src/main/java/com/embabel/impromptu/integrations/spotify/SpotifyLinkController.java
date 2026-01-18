package com.embabel.impromptu.integrations.spotify;

import com.embabel.impromptu.user.ImpromptuUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Controller for handling Spotify OAuth2 link flow.
 */
@Controller
public class SpotifyLinkController {

    private static final Logger logger = LoggerFactory.getLogger(SpotifyLinkController.class);

    private final SpotifyService spotifyService;
    private final ImpromptuUserService userService;

    @Value("${spring.security.oauth2.client.registration.spotify.client-id:}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.spotify.scope:}")
    private String scope;

    @Value("${spring.security.oauth2.client.registration.spotify.redirect-uri-override:}")
    private String redirectUriOverride;

    public SpotifyLinkController(SpotifyService spotifyService, ImpromptuUserService userService) {
        this.spotifyService = spotifyService;
        this.userService = userService;
    }

    /**
     * Initiate the Spotify OAuth flow.
     */
    @GetMapping("/link/spotify")
    public String initiateLink(HttpServletRequest request) {
        if (!spotifyService.isConfigured()) {
            logger.warn("Spotify is not configured - missing client ID or secret");
            return "redirect:/chat?error=spotify_not_configured";
        }

        String redirectUri = getRedirectUri(request);
        String authUrl = "https://accounts.spotify.com/authorize"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope.replace(",", " "), StandardCharsets.UTF_8)
                + "&show_dialog=true";

        logger.info("Redirecting to Spotify authorization: {}", authUrl);
        return "redirect:" + authUrl;
    }

    /**
     * Handle the OAuth callback from Spotify.
     */
    @GetMapping("/callback/spotify")
    public String handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {

        if (error != null) {
            logger.warn("Spotify authorization error: {}", error);
            return "redirect:/chat?error=spotify_auth_failed";
        }

        if (code == null) {
            logger.warn("No authorization code received from Spotify");
            return "redirect:/chat?error=spotify_no_code";
        }

        try {
            var user = userService.getAuthenticatedUser();
            if (user == null || "Anonymous".equals(user.getDisplayName())) {
                logger.warn("Cannot link Spotify for anonymous user");
                return "redirect:/chat?error=not_logged_in";
            }

            // Exchange code for tokens
            var redirectUri = getRedirectUri(request);
            SpotifyService.SpotifyTokenResponse tokens = spotifyService.exchangeCodeForTokens(code, redirectUri);

            // Store tokens on user
            user.setSpotifyAccessToken(tokens.accessToken());
            user.setSpotifyRefreshToken(tokens.refreshToken());
            user.setSpotifyTokenExpiry(Instant.now().plusSeconds(tokens.expiresIn()));

            // Get Spotify user ID
            SpotifyService.SpotifyUser spotifyUser = spotifyService.getCurrentUser(user);
            user.setSpotifyUserId(spotifyUser.id());

            // Persist the updated user with Spotify tokens
            userService.save(user);

            logger.info("Successfully linked Spotify for user {} (Spotify: {})",
                    user.getId(), spotifyUser.displayName());

            return "redirect:/chat?spotify=linked";

        } catch (Exception e) {
            logger.error("Failed to complete Spotify link", e);
            return "redirect:/chat?error=spotify_link_failed";
        }
    }

    private String getRedirectUri(HttpServletRequest request) {
        // Use override if configured (for production or non-localhost)
        if (redirectUriOverride != null && !redirectUriOverride.isEmpty()) {
            return redirectUriOverride;
        }

        // Check for forwarded headers (when behind a proxy/load balancer)
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedPort = request.getHeader("X-Forwarded-Port");

        String scheme = forwardedProto != null ? forwardedProto : request.getScheme();
        String serverName = forwardedHost != null ? forwardedHost : request.getServerName();
        int serverPort = forwardedPort != null ? Integer.parseInt(forwardedPort) : request.getServerPort();

        // If forwarded host contains port, don't add it again
        if (forwardedHost != null && forwardedHost.contains(":")) {
            return scheme + "://" + serverName + "/callback/spotify";
        }

        var url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if (("http".equals(scheme) && serverPort != 80)
                || ("https".equals(scheme) && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append("/callback/spotify");
        return url.toString();
    }
}
