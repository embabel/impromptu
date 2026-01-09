package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
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

        // Logo and subtitle
        var titleContainer = new HorizontalLayout();
        titleContainer.setAlignItems(Alignment.CENTER);
        titleContainer.setSpacing(true);

        var logoContainer = new Div();
        logoContainer.addClassName("header-logo-container");

        var logo = new Image("/images/piano_wide_2.jpg", "Impromptu");
        logo.addClassName("header-logo");
        logoContainer.add(logo);

        var subtitleText = new Span("Classical Music Explorer");
        subtitleText.addClassName("header-title-subtitle");

        titleContainer.add(logoContainer, subtitleText);

        // User info and logout
        var userSection = createUserSection(config);

        titleRow.add(titleContainer, userSection);

        add(titleRow);
    }

    private HorizontalLayout createUserSection(HeaderConfig config) {
        var userSection = new HorizontalLayout();
        userSection.setAlignItems(Alignment.CENTER);
        userSection.setSpacing(true);

        var user = config.user();
        var userName = new Span(user.getDisplayName());
        userName.addClassName("header-user-name");

        if (!"Anonymous".equals(user.getDisplayName())) {
            // Spotify link button (only show if Spotify is configured)
            if (config.spotifyConfigured()) {
                if (config.spotifyLinked()) {
                    // User has linked Spotify
                    var spotifyBadge = new Span("Spotify linked");
                    spotifyBadge.addClassName("spotify-badge");
                    userSection.add(spotifyBadge);
                } else {
                    // User hasn't linked Spotify yet
                    var linkSpotifyAnchor = new Anchor("/link/spotify", "Link Spotify");
                    linkSpotifyAnchor.getElement().setAttribute("router-ignore", true);
                    linkSpotifyAnchor.addClassName("spotify-link");
                    userSection.add(linkSpotifyAnchor);
                }
            }

            var logoutButton = new Button("Logout", e -> {
                getUI().ifPresent(ui -> {
                    ui.getPage().setLocation("/logout");
                });
            });
            logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            logoutButton.addClassName("header-logout-button");
            userSection.add(userName, logoutButton);
        } else {
            var loginLink = new Anchor("/login", "Sign in");
            loginLink.addClassName("header-login-link");
            userSection.add(loginLink);
        }

        return userSection;
    }
}
