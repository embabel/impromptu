package com.embabel.impromptu.user;

import com.embabel.agent.api.identity.User;
import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.model.NamedEntityData;
import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

import static com.embabel.agent.rag.model.NamedEntityData.ENTITY_LABEL;

/**
 * Mutable user class for Impromptu application.
 */
@NodeFragment(labels = {NamedEntityData.ENTITY_LABEL, "User"})
@CreationPermitted(false)
public class ImpromptuUser implements User, NamedEntity, PromptContributor {

    @NodeId
    private String id;
    private String displayName;
    private String username;
    private String email;

    // Location data from IP geolocation
    private @Nullable String countryCode;
    private @Nullable String city;
    private @Nullable String timezone;
    private @Nullable Double latitude;
    private @Nullable Double longitude;

    private String currentContextName;

    private @Nullable String spotifyAccessToken;
    private @Nullable String spotifyRefreshToken;
    private @Nullable Instant spotifyTokenExpiry;
    private @Nullable String spotifyUserId;

    // User preferences
    private boolean voiceEnabled = false;

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
    @NonNull
    public Set<String> labels() {
        return Set.of(ENTITY_LABEL, "User");
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

    // Location getters and setters

    public @Nullable String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(@Nullable String countryCode) {
        this.countryCode = countryCode;
    }

    public @Nullable String getCity() {
        return city;
    }

    public void setCity(@Nullable String city) {
        this.city = city;
    }

    public @Nullable String getTimezone() {
        return timezone;
    }

    public void setTimezone(@Nullable String timezone) {
        this.timezone = timezone;
    }

    public @Nullable Double getLatitude() {
        return latitude;
    }

    public void setLatitude(@Nullable Double latitude) {
        this.latitude = latitude;
    }

    public @Nullable Double getLongitude() {
        return longitude;
    }

    public void setLongitude(@Nullable Double longitude) {
        this.longitude = longitude;
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

    // Voice preferences

    public boolean isVoiceEnabled() {
        return voiceEnabled;
    }

    public void setVoiceEnabled(boolean voiceEnabled) {
        this.voiceEnabled = voiceEnabled;
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
