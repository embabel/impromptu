package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Header component for the chat view showing title, user info, and stats.
 */
public class ChatHeader extends VerticalLayout {

    /**
     * Configuration for the header display.
     */
    public record HeaderConfig(
            ImpromptuUser user,
            String objective,
            String persona,
            long chunkCount,
            long documentCount,
            boolean spotifyConfigured,
            boolean spotifyLinked
    ) {}

    public ChatHeader(HeaderConfig config) {
        setPadding(false);
        setSpacing(false);

        // Title row with logout
        var titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setJustifyContentMode(JustifyContentMode.BETWEEN);

        var title = new H3("Impromptu Classical Music Explorer");
        title.getStyle().set("margin", "0");

        // User info and logout
        var userSection = createUserSection(config);

        titleRow.add(title, userSection);

        // Stats line
        var statsText = new Span(String.format(
                "Objective: %s | Persona: %s | %,d chunks | %,d documents",
                config.objective() != null ? config.objective() : "Not set",
                config.persona(),
                config.chunkCount(),
                config.documentCount()
        ));
        statsText.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        add(titleRow, statsText);
    }

    private HorizontalLayout createUserSection(HeaderConfig config) {
        var userSection = new HorizontalLayout();
        userSection.setAlignItems(Alignment.CENTER);
        userSection.setSpacing(true);

        var user = config.user();
        var userName = new Span(user.getDisplayName());
        userName.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        if (!"Anonymous".equals(user.getDisplayName())) {
            // Spotify link button (only show if Spotify is configured)
            if (config.spotifyConfigured()) {
                if (config.spotifyLinked()) {
                    // User has linked Spotify
                    var spotifyBadge = new Span("Spotify linked");
                    spotifyBadge.getStyle()
                            .set("color", "var(--lumo-success-text-color)")
                            .set("font-size", "var(--lumo-font-size-xs)")
                            .set("background", "var(--lumo-success-color-10pct)")
                            .set("padding", "2px 8px")
                            .set("border-radius", "var(--lumo-border-radius-s)");
                    userSection.add(spotifyBadge);
                } else {
                    // User hasn't linked Spotify yet
                    var linkSpotifyAnchor = new Anchor("/link/spotify", "Link Spotify");
                    linkSpotifyAnchor.getElement().setAttribute("router-ignore", true);
                    linkSpotifyAnchor.getStyle()
                            .set("color", "#1DB954") // Spotify green
                            .set("font-size", "var(--lumo-font-size-s)")
                            .set("text-decoration", "none")
                            .set("padding", "4px 8px")
                            .set("border", "1px solid #1DB954")
                            .set("border-radius", "var(--lumo-border-radius-s)");
                    userSection.add(linkSpotifyAnchor);
                }
            }

            var logoutButton = new Button("Logout", e -> {
                getUI().ifPresent(ui -> {
                    ui.getPage().setLocation("/logout");
                });
            });
            logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            logoutButton.getStyle()
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
            userSection.add(userName, logoutButton);
        } else {
            var loginLink = new Anchor("/login", "Sign in");
            loginLink.getStyle()
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("text-decoration", "none");
            userSection.add(loginLink);
        }

        return userSection;
    }
}
