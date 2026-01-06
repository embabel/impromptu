package com.embabel.impromptu.user;

import com.embabel.agent.api.identity.User;
import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Mutable user class for Impromptu application.
 */
@NodeFragment(labels = {"Entity", "User"})
@CreationPermitted(false)
public class ImpromptuUser implements User, NamedEntity, PromptContributor {

    @NodeId
    private String id;
    private String displayName;
    private String username;
    private String email;

    private String currentContextName;

    private @Nullable String spotifyAccessToken;
    private @Nullable String spotifyRefreshToken;
    private @Nullable Instant spotifyTokenExpiry;
    private @Nullable String spotifyUserId;

    @JsonCreator
    public ImpromptuUser(
            @JsonProperty("id") String id,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("username") String username,
            @JsonProperty("email") String email) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.email = email;
        this.currentContextName = "default";
    }

    @Override
    public @NonNull String contribution() {
        return toString();
    }

    @Override
    public @NonNull String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public @NonNull String getName() {
        return displayName;
    }

    @Override
    public @NonNull String getDescription() {
        return "User %s with username %s".formatted(displayName, username);
    }

    // TODO this is questionable, needed for Vaadin to not crash with serialization errors
    @Override
    @JsonIgnore
    public java.util.Map<String, Object> getMetadata() {
        return java.util.Map.of();
    }

    /**
     * Return labels matching the @NodeFragment annotation.
     * This ensures EntityIdentifier.forUser() can find propositions mentioning this user.
     */
    @Override
    public java.util.Set<String> labels() {
        return java.util.Set.of("Entity", "User");
    }

    /**
     * The name of the context in which the user is working.
     * Will be combined with user id to create a default context for propositions.
     */
    public @NonNull String getCurrentContextName() {
        return currentContextName;
    }

    public void setCurrentContextName(@NonNull String currentContextName) {
        this.currentContextName = currentContextName;
    }

    /**
     * Get the full current context id for the user.
     * This should be used to retrieve propositions
     */
    @NonNull
    public String currentContext() {
        return "%s_%s".formatted(id, currentContextName);
    }

    @Override
    public @NonNull String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public @NonNull String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Spotify getters and setters

    public @Nullable String getSpotifyAccessToken() {
        return spotifyAccessToken;
    }

    public void setSpotifyAccessToken(@Nullable String spotifyAccessToken) {
        this.spotifyAccessToken = spotifyAccessToken;
    }

    public @Nullable String getSpotifyRefreshToken() {
        return spotifyRefreshToken;
    }

    public void setSpotifyRefreshToken(@Nullable String spotifyRefreshToken) {
        this.spotifyRefreshToken = spotifyRefreshToken;
    }

    public @Nullable Instant getSpotifyTokenExpiry() {
        return spotifyTokenExpiry;
    }

    public void setSpotifyTokenExpiry(@Nullable Instant spotifyTokenExpiry) {
        this.spotifyTokenExpiry = spotifyTokenExpiry;
    }

    public @Nullable String getSpotifyUserId() {
        return spotifyUserId;
    }

    public void setSpotifyUserId(@Nullable String spotifyUserId) {
        this.spotifyUserId = spotifyUserId;
    }

    public boolean isSpotifyLinked() {
        return spotifyAccessToken != null && spotifyRefreshToken != null;
    }

    @Override
    public String toString() {
        return "ImpromptuUser{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
