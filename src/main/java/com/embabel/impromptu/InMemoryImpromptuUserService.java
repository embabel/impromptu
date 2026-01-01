package com.embabel.impromptu;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory user service that provisions users on first login.
 */
public class InMemoryImpromptuUserService extends ImpromptuUserService {

    private final ConcurrentMap<String, ImpromptuUser> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ImpromptuUser> usersByEmail = new ConcurrentHashMap<>();

    @Override
    protected ImpromptuUser findOrCreate(String id, String displayName, String username, String email) {
        // Try to find existing user by ID
        ImpromptuUser existing = usersById.get(id);
        if (existing != null) {
            logger.info("Found existing user by ID: {}", existing);
            return existing;
        }

        // Try to find by email as fallback
        if (email != null) {
            existing = usersByEmail.get(email);
            if (existing != null) {
                logger.info("Found existing user by email: {}", existing);
                return existing;
            }
        }

        // Create new user
        var newUser = new ImpromptuUser(id, displayName, username, email);
        usersById.put(id, newUser);
        if (email != null) {
            usersByEmail.put(email, newUser);
        }
        logger.info("Provisioned new user: {}", newUser);
        return newUser;
    }

    @Override
    public @Nullable ImpromptuUser findById(@NonNull String id) {
        return usersById.get(id);
    }

    @Override
    public @Nullable ImpromptuUser findByUsername(@NonNull String username) {
        return usersById.values().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable ImpromptuUser findByEmail(@NonNull String email) {
        return usersByEmail.get(email);
    }
}
