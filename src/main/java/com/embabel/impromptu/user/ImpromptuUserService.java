package com.embabel.impromptu.user;

import com.embabel.agent.api.identity.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.UUID;

public abstract class ImpromptuUserService implements UserService<ImpromptuUser> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Find or create a user by ID.
     */
    protected abstract ImpromptuUser findOrCreate(String id, String displayName, String username, String email);

    /**
     * Save/update a user.
     */
    public abstract ImpromptuUser save(ImpromptuUser user);

    /**
     * Gets the authenticated user from Google OAuth, or an anonymous user if not authenticated.
     * Looks up existing user or provisions a new one.
     */
    public ImpromptuUser getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            // Google OAuth provides these attributes
            String id = oauth2User.getAttribute("sub"); // Google's unique user ID
            String displayName = oauth2User.getAttribute("name");
            String email = oauth2User.getAttribute("email");
            // Prefer Google's sub (stable user ID), fall back to email for persistence
            var finalId = id != null ? id : email;
            if (finalId == null) {
                throw new IllegalStateException("OAuth2 user has neither 'sub' nor 'email' - cannot identify user");
            }
            return findOrCreate(
                    finalId,
                    displayName != null ? displayName : "User",
                    email != null ? email : "unknown",
                    email
            );
        }
        // Return anonymous user (not persisted)
        return new ImpromptuUser(UUID.randomUUID().toString(), "Anonymous", "anonymous", null);
    }
}
