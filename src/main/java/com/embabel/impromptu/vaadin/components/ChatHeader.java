package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
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
            boolean spotifyLinked,
            Runnable onUserProfileClick
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

        if (!"Anonymous".equals(user.getDisplayName())) {
            // Spotify link button (only show if Spotify is configured)
            if (config.spotifyConfigured()) {
                if (config.spotifyLinked()) {
                    var spotifyBadge = new Span("Spotify linked");
                    spotifyBadge.addClassName("spotify-badge");
                    userSection.add(spotifyBadge);
                } else {
                    var linkSpotifyAnchor = new Anchor("/link/spotify", "Link Spotify");
                    linkSpotifyAnchor.getElement().setAttribute("router-ignore", true);
                    linkSpotifyAnchor.addClassName("spotify-link");
                    userSection.add(linkSpotifyAnchor);
                }
            }

            // User profile chip - clickable avatar + name
            var profileChip = new HorizontalLayout();
            profileChip.setAlignItems(Alignment.CENTER);
            profileChip.setSpacing(false);
            profileChip.addClassName("user-profile-chip");
            profileChip.getStyle()
                    .set("background", "var(--lumo-contrast-5pct)")
                    .set("border-radius", "20px")
                    .set("padding", "4px 12px 4px 4px")
                    .set("cursor", "pointer")
                    .set("gap", "8px");

            // Avatar with initials
            var initials = getInitials(user.getDisplayName());
            var avatar = new Div();
            avatar.setText(initials);
            avatar.getStyle()
                    .set("width", "28px")
                    .set("height", "28px")
                    .set("border-radius", "50%")
                    .set("background", "var(--lumo-primary-color)")
                    .set("color", "var(--lumo-primary-contrast-color)")
                    .set("display", "flex")
                    .set("align-items", "center")
                    .set("justify-content", "center")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("font-weight", "600");

            var userName = new Span(user.getDisplayName());
            userName.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-body-text-color)");

            profileChip.add(avatar, userName);

            if (config.onUserProfileClick() != null) {
                profileChip.addClickListener(e -> config.onUserProfileClick().run());
            }

            var logoutButton = new Button("Logout", e -> {
                getUI().ifPresent(ui -> {
                    ui.getPage().setLocation("/logout");
                });
            });
            logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            logoutButton.addClassName("header-logout-button");
            userSection.add(profileChip, logoutButton);
        } else {
            var loginLink = new Anchor("/login", "Sign in");
            loginLink.addClassName("header-login-link");
            userSection.add(loginLink);
        }

        return userSection;
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        var parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
