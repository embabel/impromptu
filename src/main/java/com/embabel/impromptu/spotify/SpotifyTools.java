package com.embabel.impromptu.spotify;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM tools for Spotify integration.
 * These tools are available to the chatbot when the user has linked their Spotify account.
 *
 */
public record SpotifyTools(
        ImpromptuUser user,
        SpotifyService spotifyService) {

    private static final Logger logger = LoggerFactory.getLogger(SpotifyTools.class);

    /**
     * Check if Spotify is available for the current user.
     */
    @LlmTool(description = "Check if user has linked their Spotify account")
    public String checkSpotifyStatus() {
        if (!spotifyService.isConfigured()) {
            return "Spotify integration is not configured on this server.";
        }

        if (user == null) {
            return "Unable to determine current user.";
        }

        if (!spotifyService.isLinked(user)) {
            return "You haven't linked your Spotify account yet. Click 'Link Spotify' in the header to connect your account.";
        }

        return "Your Spotify account is linked and ready to use!";
    }

    /**
     * Get user's Spotify playlists.
     */
    @LlmTool(description = "Get the user's Spotify playlists")
    public String getPlaylists() {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first by clicking 'Link Spotify' in the header.";
        }

        try {
            List<SpotifyService.SpotifyPlaylist> playlists = spotifyService.getUserPlaylists(user);
            if (playlists.isEmpty()) {
                return "You don't have any playlists yet.";
            }

            StringBuilder sb = new StringBuilder("Your Spotify playlists:\n\n");
            for (SpotifyService.SpotifyPlaylist playlist : playlists) {
                sb.append("- **").append(playlist.name()).append("** (")
                        .append(playlist.trackCount()).append(" tracks)\n");
            }
            return sb.toString();
        } catch (SpotifyException e) {
            logger.error("Failed to get playlists", e);
            return "Failed to get your playlists: " + e.getMessage();
        }
    }

    /**
     * Search for tracks on Spotify.
     */
    @LlmTool(description = "Search for tracks on Spotify by song name, artist, or both")
    public String searchTracks(
            String query
    ) {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first by clicking 'Link Spotify' in the header.";
        }

        try {
            List<SpotifyService.SpotifyTrack> tracks = spotifyService.searchTracks(user, query, 10);
            if (tracks.isEmpty()) {
                return "No tracks found for: " + query;
            }

            StringBuilder sb = new StringBuilder("Found tracks:\n\n");
            for (int i = 0; i < tracks.size(); i++) {
                SpotifyService.SpotifyTrack track = tracks.get(i);
                sb.append(i + 1).append(". **").append(track.name()).append("** by ")
                        .append(track.artist()).append("\n");
            }
            return sb.toString();
        } catch (SpotifyException e) {
            logger.error("Failed to search tracks", e);
            return "Failed to search for tracks: " + e.getMessage();
        }
    }

    /**
     * Create a new Spotify playlist.
     */
    @LlmTool(description = "Create a new Spotify playlist with the given name and description")
    public String createPlaylist(
            String name,
            String description) {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first by clicking 'Link Spotify' in the header.";
        }

        try {
            SpotifyService.SpotifyPlaylist playlist = spotifyService.createPlaylist(
                    user, name, description != null ? description : "", false);
            logger.info("Created playlist '{}' for user {}", name, user.getId());
            return "Created playlist: **" + playlist.name() + "**";
        } catch (SpotifyException e) {
            logger.error("Failed to create playlist", e);
            return "Failed to create playlist: " + e.getMessage();
        }
    }

    /**
     * Search for tracks and add them to a playlist.
     */
//    @LlmTool(description = "Search for tracks and add matching results to an existing playlist")
    public String addTracksToPlaylist(
            String playlistName,
            List<String> trackQueries) {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first by clicking 'Link Spotify' in the header.";
        }

        try {
            // Find the playlist
            List<SpotifyService.SpotifyPlaylist> playlists = spotifyService.getUserPlaylists(user);
            SpotifyService.SpotifyPlaylist targetPlaylist = playlists.stream()
                    .filter(p -> p.name().equalsIgnoreCase(playlistName))
                    .findFirst()
                    .orElse(null);

            if (targetPlaylist == null) {
                return "Could not find a playlist named '" + playlistName + "'. Use getPlaylists to see your available playlists.";
            }

            // Search and collect track URIs
            List<String> trackUris = trackQueries.stream()
                    .map(query -> {
                        List<SpotifyService.SpotifyTrack> results = spotifyService.searchTracks(user, query, 1);
                        return results.isEmpty() ? null : results.get(0).uri();
                    })
                    .filter(uri -> uri != null)
                    .collect(Collectors.toList());

            if (trackUris.isEmpty()) {
                return "Could not find any of the requested tracks on Spotify.";
            }

            // Add tracks to playlist
            spotifyService.addTracksToPlaylist(user, targetPlaylist.id(), trackUris);

            logger.info("Added {} tracks to playlist '{}' for user {}",
                    trackUris.size(), playlistName, user.getId());

            return "Added " + trackUris.size() + " track(s) to **" + playlistName + "**" +
                    (trackUris.size() < trackQueries.size()
                            ? " (" + (trackQueries.size() - trackUris.size()) + " tracks not found)"
                            : "");
        } catch (SpotifyException e) {
            logger.error("Failed to add tracks to playlist", e);
            return "Failed to add tracks: " + e.getMessage();
        }
    }

    /**
     * Create a new playlist and add tracks to it in one action.
     */
//    @LlmTool(description = "Create a new playlist and populate it with tracks found by searching")
    public String createPlaylistWithTracks(
            String playlistName,
            String description,
            List<String> trackQueries) {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first by clicking 'Link Spotify' in the header.";
        }

        try {
            // Create the playlist
            SpotifyService.SpotifyPlaylist playlist = spotifyService.createPlaylist(
                    user, playlistName, description != null ? description : "", false);

            // Search and collect track URIs
            List<String> trackUris = trackQueries.stream()
                    .map(query -> {
                        List<SpotifyService.SpotifyTrack> results = spotifyService.searchTracks(user, query, 1);
                        return results.isEmpty() ? null : results.get(0).uri();
                    })
                    .filter(uri -> uri != null)
                    .collect(Collectors.toList());

            if (!trackUris.isEmpty()) {
                spotifyService.addTracksToPlaylist(user, playlist.id(), trackUris);
            }

            logger.info("Created playlist '{}' with {} tracks for user {}",
                    playlistName, trackUris.size(), user.getId());

            return "Created playlist **" + playlistName + "** with " + trackUris.size() + " track(s)" +
                    (trackUris.size() < trackQueries.size()
                            ? " (" + (trackQueries.size() - trackUris.size()) + " tracks not found)"
                            : "");
        } catch (SpotifyException e) {
            logger.error("Failed to create playlist with tracks", e);
            return "Failed to create playlist: " + e.getMessage();
        }
    }
}
