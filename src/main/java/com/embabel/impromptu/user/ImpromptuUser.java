package com.embabel.impromptu.user;

import com.embabel.agent.api.identity.User;
import com.embabel.common.ai.prompt.PromptContributor;
import org.jspecify.annotations.NonNull;

/**
 * Mutable user class for Impromptu application.
 */
public class ImpromptuUser implements User, PromptContributor {

    private String id;
    private String displayName;
    private String username;
    private String email;

    public ImpromptuUser(String id, String displayName, String username, String email) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.email = email;
    }

    @Override
    public @NonNull String contribution() {
        return toString();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getUsername() {
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
