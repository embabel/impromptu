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

    // ========== Playback Control Tools ==========

    /**
     * Get current playback status.
     */
    @LlmTool(description = "Get what's currently playing on the user's Spotify")
    public String getNowPlaying() {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            var state = spotifyService.getPlaybackState(user);
            if (!state.isActive() || state.trackName() == null) {
                return "Nothing is currently playing. Start playback on a Spotify device first.";
            }

            String status = state.isPlaying() ? "Now playing" : "Paused";
            return String.format("%s: **%s** by %s (on %s)",
                    status,
                    state.trackName(),
                    state.artistName() != null ? state.artistName() : "Unknown",
                    state.deviceName() != null ? state.deviceName() : "Unknown device");
        } catch (SpotifyException e) {
            logger.error("Failed to get playback state", e);
            return "Failed to get playback status: " + e.getMessage();
        }
    }

    /**
     * Play a classical work by searching for it.
     * Finds the best matching track and queues all movements of the work.
     */
    @LlmTool(description = "Play a classical work on Spotify by searching for it (e.g., 'Brahms Violin Sonata No. 1 Perlman'). Will find all movements and play them in order.")
    public String playTrack(String query) {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            // Search with more results for better matching
            List<SpotifyService.SpotifyTrackDetails> tracks = spotifyService.searchTracksDetailed(user, query, 15);
            if (tracks.isEmpty()) {
                return "No tracks found for: " + query;
            }

            // Score each track and find best match
            var bestMatch = tracks.stream()
                    .max((a, b) -> Integer.compare(a.scoreMatch(query), b.scoreMatch(query)))
                    .orElse(tracks.get(0));

            int bestScore = bestMatch.scoreMatch(query);
            logger.info("Best match for '{}': {} (score: {})", query, bestMatch.displayString(), bestScore);

            // If score is very low, warn but proceed
            if (bestScore < 30) {
                logger.warn("Low confidence match for '{}': {}", query, bestMatch.displayString());
            }

            // Get album tracks to find all movements
            List<String> trackUris;
            String movementInfo = "";

            if (bestMatch.albumId() != null) {
                var albumTracks = spotifyService.getAlbumTracks(user, bestMatch.albumId());
                var movements = spotifyService.findWorkMovements(albumTracks, bestMatch.toAlbumTrack(), query);

                if (movements.size() > 1) {
                    trackUris = movements.stream().map(SpotifyService.AlbumTrack::uri).toList();
                    movementInfo = " (" + movements.size() + " movements)";
                    logger.info("Found {} movements for work", movements.size());
                } else {
                    trackUris = List.of(bestMatch.uri());
                }
            } else {
                trackUris = List.of(bestMatch.uri());
            }

            // Start playback with all movement tracks
            spotifyService.play(user, null, trackUris);

            logger.info("Started playing '{}' by {} for user {} - {} track(s)",
                    bestMatch.name(), bestMatch.artist(), user.getId(), trackUris.size());

            return "Now playing: **" + bestMatch.name() + "** by " + bestMatch.artist() +
                    " (from " + bestMatch.albumName() + ")" + movementInfo;
        } catch (SpotifyException e) {
            logger.error("Failed to play track", e);
            if (e.getMessage() != null && e.getMessage().contains("No active device")) {
                return "No active Spotify device found. Please open Spotify on one of your devices first.";
            }
            return "Failed to play track: " + e.getMessage();
        }
    }

    /**
     * Play a playlist by name.
     */
    @LlmTool(description = "Play one of the user's Spotify playlists by name")
    public String playPlaylist(String playlistName) {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            // Find the playlist
            List<SpotifyService.SpotifyPlaylist> playlists = spotifyService.getUserPlaylists(user);
            var playlist = playlists.stream()
                    .filter(p -> p.name().equalsIgnoreCase(playlistName))
                    .findFirst()
                    .orElse(null);

            if (playlist == null) {
                // Try partial match
                playlist = playlists.stream()
                        .filter(p -> p.name().toLowerCase().contains(playlistName.toLowerCase()))
                        .findFirst()
                        .orElse(null);
            }

            if (playlist == null) {
                return "Could not find a playlist named '" + playlistName + "'. Use getPlaylists to see your available playlists.";
            }

            // Start playback
            spotifyService.play(user, "spotify:playlist:" + playlist.id(), null);

            logger.info("Started playing playlist '{}' for user {}", playlist.name(), user.getId());

            return "Now playing playlist: **" + playlist.name() + "** (" + playlist.trackCount() + " tracks)";
        } catch (SpotifyException e) {
            logger.error("Failed to play playlist", e);
            if (e.getMessage() != null && e.getMessage().contains("No active device")) {
                return "No active Spotify device found. Please open Spotify on one of your devices first.";
            }
            return "Failed to play playlist: " + e.getMessage();
        }
    }

    /**
     * Pause playback.
     */
    @LlmTool(description = "Pause the current Spotify playback")
    public String pausePlayback() {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            spotifyService.pause(user);
            return "Playback paused.";
        } catch (SpotifyException e) {
            logger.error("Failed to pause playback", e);
            return "Failed to pause: " + e.getMessage();
        }
    }

    /**
     * Resume playback.
     */
    @LlmTool(description = "Resume Spotify playback")
    public String resumePlayback() {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            spotifyService.play(user);
            return "Playback resumed.";
        } catch (SpotifyException e) {
            logger.error("Failed to resume playback", e);
            if (e.getMessage() != null && e.getMessage().contains("No active device")) {
                return "No active Spotify device found. Please open Spotify on one of your devices first.";
            }
            return "Failed to resume: " + e.getMessage();
        }
    }

    /**
     * Skip to next track.
     */
    @LlmTool(description = "Skip to the next track on Spotify")
    public String skipToNext() {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            spotifyService.skipToNext(user);
            // Brief pause to let Spotify update
            Thread.sleep(300);
            var state = spotifyService.getPlaybackState(user);
            if (state.trackName() != null) {
                return "Skipped to: **" + state.trackName() + "** by " +
                        (state.artistName() != null ? state.artistName() : "Unknown");
            }
            return "Skipped to next track.";
        } catch (Exception e) {
            logger.error("Failed to skip to next", e);
            return "Failed to skip: " + e.getMessage();
        }
    }

    /**
     * Get available devices.
     */
    @LlmTool(description = "Get the user's available Spotify devices")
    public String getDevices() {
        if (user == null || !spotifyService.isLinked(user)) {
            return "Please link your Spotify account first.";
        }

        try {
            List<SpotifyService.SpotifyDevice> devices = spotifyService.getDevices(user);
            if (devices.isEmpty()) {
                return "No Spotify devices found. Make sure Spotify is open on at least one device.";
            }

            StringBuilder sb = new StringBuilder("Available Spotify devices:\n\n");
            for (SpotifyService.SpotifyDevice device : devices) {
                sb.append("- **").append(device.name()).append("** (").append(device.type()).append(")");
                if (device.isActive()) {
                    sb.append(" ✓ active");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (SpotifyException e) {
            logger.error("Failed to get devices", e);
            return "Failed to get devices: " + e.getMessage();
        }
    }
}
