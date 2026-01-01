package com.embabel.impromptu;

import com.embabel.agent.api.identity.User;

/**
 * User record for Impromptu application.
 */
public record ImpromptuUser(
        String id,
        String displayName,
        String username,
        String email
) implements User {

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getEmail() {
        return email;
    }
}
