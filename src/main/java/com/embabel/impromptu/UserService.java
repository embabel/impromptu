package com.embabel.impromptu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for managing user authentication.
 */
@Service
public class UserService {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);

    /**
     * Gets the authenticated user from Google OAuth, or an anonymous user if not authenticated.
     */
    public ImpromptuUser getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            // Google OAuth provides these attributes
            String id = oauth2User.getAttribute("sub"); // Google's unique user ID
            String displayName = oauth2User.getAttribute("name");
            String email = oauth2User.getAttribute("email");
            // Use email as username for Google OAuth
            // Prefer Google's sub (stable user ID), fall back to email for persistence
            var finalId = id != null ? id : email;
            if (finalId == null) {
                throw new IllegalStateException("OAuth2 user has neither 'sub' nor 'email' - cannot identify user");
            }
            var user = new ImpromptuUser(
                    finalId,
                    displayName != null ? displayName : "User",
                    email != null ? email : "unknown",
                    email
            );
            logger.info("Authenticated user: {}", user);
            return user;
        }
        // Return anonymous user
        return new ImpromptuUser(UUID.randomUUID().toString(), "Anonymous", "anonymous", null);
    }
}
