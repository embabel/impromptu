package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.spotify.SpotifyService;
import com.embabel.impromptu.spotify.SpotifyService.PlaybackState;
import com.embabel.impromptu.spotify.SpotifyService.SpotifyDevice;
import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Collapsible Spotify Connect player panel for controlling playback.
 * Starts collapsed, auto-expands when playback is active.
 */
public class SpotifyPlayerPanel extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(SpotifyPlayerPanel.class);

    private final SpotifyService spotifyService;
    private final ImpromptuUser user;

    // Header components (always visible)
    private final Span headerTrackInfo;
    private final Button expandCollapseButton;

    // Collapsible content
    private final VerticalLayout contentLayout;
    private final Image albumArt;
    private final Span trackName;
    private final Span artistName;
    private final Button prevButton;
    private final Button playPauseButton;
    private final Button nextButton;
    private final ComboBox<SpotifyDevice> deviceSelector;

    private PlaybackState currentState = PlaybackState.inactive();
    private boolean isExpanded = false;

    public SpotifyPlayerPanel(SpotifyService spotifyService, ImpromptuUser user) {
        this.spotifyService = spotifyService;
        this.user = user;

        setPadding(true);
        setSpacing(false);
        setWidthFull();
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        // Header (always visible, clickable to expand/collapse)
        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.getStyle().set("cursor", "pointer");
        header.addClickListener(e -> toggleExpanded());

        var titleRow = new HorizontalLayout();
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setSpacing(true);

        var brandLabel = new Span("Spotify");
        brandLabel.getStyle()
                .set("font-weight", "bold")
                .set("color", "#1DB954"); // Spotify green

        headerTrackInfo = new Span();
        headerTrackInfo.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("max-width", "300px")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");

        titleRow.add(brandLabel, headerTrackInfo);

        var headerButtons = new HorizontalLayout();
        headerButtons.setSpacing(false);
        headerButtons.setAlignItems(Alignment.CENTER);

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.addClickListener(e -> {
            e.getSource().getElement().executeJs("event.stopPropagation()");
            refresh();
        });
        refreshButton.getElement().setAttribute("title", "Refresh");

        expandCollapseButton = new Button(VaadinIcon.CHEVRON_DOWN.create());
        expandCollapseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        expandCollapseButton.addClickListener(e -> {
            e.getSource().getElement().executeJs("event.stopPropagation()");
            toggleExpanded();
        });

        headerButtons.add(refreshButton, expandCollapseButton);
        header.add(titleRow, headerButtons);
        add(header);

        // Collapsible content
        contentLayout = new VerticalLayout();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);
        contentLayout.setWidthFull();
        contentLayout.setVisible(false); // Start collapsed

        // Player content row
        var playerContent = new HorizontalLayout();
        playerContent.setWidthFull();
        playerContent.setSpacing(true);
        playerContent.setAlignItems(Alignment.CENTER);

        // Album art
        albumArt = new Image();
        albumArt.setWidth("60px");
        albumArt.setHeight("60px");
        albumArt.getStyle()
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("object-fit", "cover");

        // Track info
        var trackInfo = new VerticalLayout();
        trackInfo.setPadding(false);
        trackInfo.setSpacing(false);

        trackName = new Span();
        trackName.getStyle()
                .set("font-weight", "500")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("max-width", "200px");

        artistName = new Span();
        artistName.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("max-width", "200px");

        trackInfo.add(trackName, artistName);

        // Transport controls
        var controls = new HorizontalLayout();
        controls.setSpacing(false);
        controls.setAlignItems(Alignment.CENTER);

        prevButton = new Button(VaadinIcon.STEP_BACKWARD.create());
        prevButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        prevButton.addClickListener(e -> skipToPrevious());

        playPauseButton = new Button(VaadinIcon.PLAY.create());
        playPauseButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        playPauseButton.addClickListener(e -> togglePlayPause());

        nextButton = new Button(VaadinIcon.STEP_FORWARD.create());
        nextButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        nextButton.addClickListener(e -> skipToNext());

        controls.add(prevButton, playPauseButton, nextButton);

        playerContent.add(albumArt, trackInfo, controls);
        playerContent.setFlexGrow(1, trackInfo);

        // Device selector
        deviceSelector = new ComboBox<>("Device");
        deviceSelector.setWidthFull();
        deviceSelector.setItemLabelGenerator(d -> d.name() + " (" + d.type() + ")");
        deviceSelector.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                transferToDevice(e.getValue());
            }
        });

        contentLayout.add(playerContent, deviceSelector);
        add(contentLayout);

        // Initial refresh
        refresh();
    }

    private void toggleExpanded() {
        isExpanded = !isExpanded;
        contentLayout.setVisible(isExpanded);
        expandCollapseButton.setIcon(isExpanded
                ? VaadinIcon.CHEVRON_UP.create()
                : VaadinIcon.CHEVRON_DOWN.create());
    }

    public void expand() {
        if (!isExpanded) {
            toggleExpanded();
        }
    }

    public void collapse() {
        if (isExpanded) {
            toggleExpanded();
        }
    }

    /**
     * Refresh playback state and device list.
     */
    public void refresh() {
        var ui = UI.getCurrent();
        if (ui == null) return;

        new Thread(() -> {
            try {
                var state = spotifyService.getPlaybackState(user);
                var devices = spotifyService.getDevices(user);

                ui.access(() -> {
                    updatePlaybackState(state);
                    updateDevices(devices, state.deviceId());
                });
            } catch (Exception e) {
                logger.warn("Failed to refresh Spotify state: {}", e.getMessage());
                ui.access(() -> updatePlaybackState(PlaybackState.inactive()));
            }
        }).start();
    }

    private void updatePlaybackState(PlaybackState state) {
        this.currentState = state;

        if (!state.isActive() || state.trackName() == null) {
            headerTrackInfo.setText("");
            trackName.setText("");
            artistName.setText("");
            albumArt.setVisible(false);
            playPauseButton.setIcon(VaadinIcon.PLAY.create());
            return;
        }

        // Update header with current track
        headerTrackInfo.setText("• " + state.trackName());

        trackName.setText(state.trackName());
        artistName.setText(state.artistName() != null ? state.artistName() : "");

        if (state.albumImageUrl() != null) {
            albumArt.setSrc(state.albumImageUrl());
            albumArt.setVisible(true);
        } else {
            albumArt.setVisible(false);
        }

        // Update play/pause button
        playPauseButton.setIcon(state.isPlaying()
                ? VaadinIcon.PAUSE.create()
                : VaadinIcon.PLAY.create());

        // Auto-expand when playing
        if (state.isPlaying()) {
            expand();
        }
    }

    private void updateDevices(List<SpotifyDevice> devices, String activeDeviceId) {
        deviceSelector.setItems(devices);

        devices.stream()
                .filter(d -> d.id().equals(activeDeviceId))
                .findFirst()
                .ifPresent(deviceSelector::setValue);
    }

    private void togglePlayPause() {
        var ui = UI.getCurrent();
        new Thread(() -> {
            try {
                if (currentState.isPlaying()) {
                    spotifyService.pause(user);
                } else {
                    spotifyService.play(user);
                }
                Thread.sleep(300);
                if (ui != null) {
                    ui.access(this::refresh);
                }
            } catch (Exception e) {
                logger.warn("Failed to toggle playback: {}", e.getMessage());
                if (ui != null) {
                    ui.access(() -> showError(e));
                }
            }
        }).start();
    }

    private void showError(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains("Restricted device")) {
            String deviceHint = currentState.deviceName() != null
                    ? " Use Spotify on " + currentState.deviceName() + " to control playback."
                    : " Select a controllable device like your phone or computer.";
            com.vaadin.flow.component.notification.Notification.show(
                    "This device can't be controlled via the web." + deviceHint,
                    5000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
            );
        } else if (message != null && message.contains("No active device")) {
            com.vaadin.flow.component.notification.Notification.show(
                    "No active Spotify device. Start playback on a device first.",
                    5000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
            );
        } else {
            com.vaadin.flow.component.notification.Notification.show(
                    "Spotify error: " + (message != null ? message : "Unknown error"),
                    5000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
            );
        }
    }

    private void skipToNext() {
        var ui = UI.getCurrent();
        new Thread(() -> {
            try {
                spotifyService.skipToNext(user);
                Thread.sleep(300);
                if (ui != null) {
                    ui.access(this::refresh);
                }
            } catch (Exception e) {
                logger.warn("Failed to skip to next: {}", e.getMessage());
                if (ui != null) {
                    ui.access(() -> showError(e));
                }
            }
        }).start();
    }

    private void skipToPrevious() {
        var ui = UI.getCurrent();
        new Thread(() -> {
            try {
                spotifyService.skipToPrevious(user);
                Thread.sleep(300);
                if (ui != null) {
                    ui.access(this::refresh);
                }
            } catch (Exception e) {
                logger.warn("Failed to skip to previous: {}", e.getMessage());
                if (ui != null) {
                    ui.access(() -> showError(e));
                }
            }
        }).start();
    }

    private void transferToDevice(SpotifyDevice device) {
        var ui = UI.getCurrent();
        new Thread(() -> {
            try {
                spotifyService.transferPlayback(user, device.id(), currentState.isPlaying());
                Thread.sleep(500);
                if (ui != null) {
                    ui.access(this::refresh);
                }
            } catch (Exception e) {
                logger.warn("Failed to transfer playback: {}", e.getMessage());
                if (ui != null) {
                    ui.access(() -> showError(e));
                }
            }
        }).start();
    }
}
