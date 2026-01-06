package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.spotify.SpotifyService;
import com.embabel.impromptu.spotify.SpotifyService.PlaybackState;
import com.embabel.impromptu.spotify.SpotifyService.SpotifyDevice;
import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Spotify Connect player panel for controlling playback on remote devices.
 * Shows now playing info, transport controls, and device selector.
 */
public class SpotifyPlayerPanel extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(SpotifyPlayerPanel.class);

    private final SpotifyService spotifyService;
    private final ImpromptuUser user;

    // UI Components
    private final Image albumArt;
    private final Span trackName;
    private final Span artistName;
    private final Button prevButton;
    private final Button playPauseButton;
    private final Button nextButton;
    private final ComboBox<SpotifyDevice> deviceSelector;
    private final Button refreshButton;
    private final Div noPlaybackMessage;
    private final HorizontalLayout playerContent;

    private PlaybackState currentState = PlaybackState.inactive();

    public SpotifyPlayerPanel(SpotifyService spotifyService, ImpromptuUser user) {
        this.spotifyService = spotifyService;
        this.user = user;

        setPadding(true);
        setSpacing(true);
        setWidthFull();
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        // Header with title and refresh
        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        var title = new Span("Spotify");
        title.getStyle()
                .set("font-weight", "bold")
                .set("color", "#1DB954"); // Spotify green

        refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.addClickListener(e -> refresh());
        refreshButton.getElement().setAttribute("title", "Refresh");

        header.add(title, refreshButton);
        add(header);

        // No playback message
        noPlaybackMessage = new Div();
        noPlaybackMessage.setText("No active playback. Start playing on any Spotify device to control it here.");
        noPlaybackMessage.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("padding", "var(--lumo-space-m) 0");

        // Player content (hidden when no playback)
        playerContent = new HorizontalLayout();
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
                .set("max-width", "150px");

        artistName = new Span();
        artistName.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("max-width", "150px");

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

        add(noPlaybackMessage, playerContent, deviceSelector);

        // Initial state
        playerContent.setVisible(false);

        // Initial refresh
        refresh();
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
                ui.access(() -> {
                    updatePlaybackState(PlaybackState.inactive());
                });
            }
        }).start();
    }

    private void updatePlaybackState(PlaybackState state) {
        this.currentState = state;

        if (!state.isActive() || state.trackName() == null) {
            noPlaybackMessage.setVisible(true);
            playerContent.setVisible(false);
            return;
        }

        noPlaybackMessage.setVisible(false);
        playerContent.setVisible(true);

        trackName.setText(state.trackName());
        artistName.setText(state.artistName() != null ? state.artistName() : "");

        if (state.albumImageUrl() != null) {
            albumArt.setSrc(state.albumImageUrl());
            albumArt.setVisible(true);
        } else {
            albumArt.setVisible(false);
        }

        // Update play/pause button
        if (state.isPlaying()) {
            playPauseButton.setIcon(VaadinIcon.PAUSE.create());
        } else {
            playPauseButton.setIcon(VaadinIcon.PLAY.create());
        }
    }

    private void updateDevices(List<SpotifyDevice> devices, String activeDeviceId) {
        deviceSelector.setItems(devices);

        // Select active device
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
                // Brief delay then refresh
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
