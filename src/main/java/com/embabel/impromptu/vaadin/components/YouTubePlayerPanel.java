package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.youtube.YouTubeService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YouTube player panel using IFrame Player API.
 * Premium subscribers see no ads (detected via cookies).
 */
public class YouTubePlayerPanel extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(YouTubePlayerPanel.class);

    private final YouTubeService youTubeService;
    private final String playerId;

    private final Div playerContainer;
    private final Span titleLabel;
    private final Span channelLabel;
    private final Button playPauseButton;
    private final Div noVideoMessage;
    private final HorizontalLayout controlsLayout;

    private String currentVideoId;
    private String currentTitle;
    private boolean isPlaying = false;

    public YouTubePlayerPanel(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
        this.playerId = "yt-player-" + System.currentTimeMillis();

        setPadding(true);
        setSpacing(true);
        setWidthFull();
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        // Header
        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        var title = new Span("YouTube");
        title.getStyle()
                .set("font-weight", "bold")
                .set("color", "#FF0000"); // YouTube red

        header.add(title);
        add(header);

        // No video message
        noVideoMessage = new Div();
        noVideoMessage.setText("No video loaded. Ask me to play something on YouTube!");
        noVideoMessage.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("padding", "var(--lumo-space-m) 0");

        // Player container (will hold the iframe)
        playerContainer = new Div();
        playerContainer.setId(playerId);
        playerContainer.setWidthFull();
        playerContainer.getStyle()
                .set("aspect-ratio", "16/9")
                .set("background", "#000")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("overflow", "hidden");
        playerContainer.setVisible(false);

        // Video info
        var infoLayout = new VerticalLayout();
        infoLayout.setPadding(false);
        infoLayout.setSpacing(false);

        titleLabel = new Span();
        titleLabel.getStyle()
                .set("font-weight", "500")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");

        channelLabel = new Span();
        channelLabel.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");

        infoLayout.add(titleLabel, channelLabel);

        // Controls
        controlsLayout = new HorizontalLayout();
        controlsLayout.setSpacing(true);
        controlsLayout.setAlignItems(Alignment.CENTER);
        controlsLayout.setVisible(false);

        playPauseButton = new Button(VaadinIcon.PLAY.create());
        playPauseButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        playPauseButton.addClickListener(e -> togglePlayPause());

        var stopButton = new Button(VaadinIcon.STOP.create());
        stopButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        stopButton.addClickListener(e -> stop());

        controlsLayout.add(playPauseButton, stopButton, infoLayout);

        add(noVideoMessage, playerContainer, controlsLayout);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Initialize YouTube IFrame API
        initYouTubeApi();
    }

    private void initYouTubeApi() {
        getElement().executeJs("""
            // Load YouTube IFrame API if not already loaded
            if (!window.YT) {
                var tag = document.createElement('script');
                tag.src = 'https://www.youtube.com/iframe_api';
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
            }

            // Store player reference on window
            window.ytPlayers = window.ytPlayers || {};
            """);
    }

    /**
     * Load and play a video by ID.
     */
    public void loadVideo(String videoId, String title, String channelTitle) {
        this.currentVideoId = videoId;
        this.currentTitle = title;

        noVideoMessage.setVisible(false);
        playerContainer.setVisible(true);
        controlsLayout.setVisible(true);

        titleLabel.setText(title);
        channelLabel.setText(channelTitle);

        // Create or update the YouTube player
        getElement().executeJs("""
            var containerId = $0;
            var videoId = $1;
            var component = this;

            function createPlayer() {
                // Clear existing content
                var container = document.getElementById(containerId);
                if (!container) return;
                container.innerHTML = '';

                // Create player
                window.ytPlayers[containerId] = new YT.Player(containerId, {
                    videoId: videoId,
                    playerVars: {
                        'autoplay': 1,
                        'modestbranding': 1,
                        'rel': 0,
                        'fs': 1
                    },
                    events: {
                        'onStateChange': function(event) {
                            // -1: unstarted, 0: ended, 1: playing, 2: paused, 3: buffering
                            component.$server.onPlayerStateChange(event.data);
                        },
                        'onReady': function(event) {
                            event.target.playVideo();
                        }
                    }
                });
            }

            // Wait for API to be ready
            if (window.YT && window.YT.Player) {
                createPlayer();
            } else {
                window.onYouTubeIframeAPIReady = function() {
                    createPlayer();
                };
            }
            """, playerId, videoId);

        isPlaying = true;
        updatePlayPauseButton();
        logger.info("Loading YouTube video: {} - {}", videoId, title);
    }

    @ClientCallable
    public void onPlayerStateChange(int state) {
        // -1: unstarted, 0: ended, 1: playing, 2: paused, 3: buffering
        isPlaying = (state == 1 || state == 3);
        getUI().ifPresent(ui -> ui.access(this::updatePlayPauseButton));
    }

    private void togglePlayPause() {
        if (currentVideoId == null) return;

        getElement().executeJs("""
            var player = window.ytPlayers[$0];
            if (player && player.getPlayerState) {
                var state = player.getPlayerState();
                if (state === 1) {
                    player.pauseVideo();
                } else {
                    player.playVideo();
                }
            }
            """, playerId);

        isPlaying = !isPlaying;
        updatePlayPauseButton();
    }

    private void stop() {
        if (currentVideoId == null) return;

        getElement().executeJs("""
            var player = window.ytPlayers[$0];
            if (player && player.stopVideo) {
                player.stopVideo();
            }
            """, playerId);

        isPlaying = false;
        updatePlayPauseButton();
    }

    /**
     * Play the video.
     */
    public void play() {
        if (currentVideoId == null) return;

        getElement().executeJs("""
            var player = window.ytPlayers[$0];
            if (player && player.playVideo) {
                player.playVideo();
            }
            """, playerId);

        isPlaying = true;
        updatePlayPauseButton();
    }

    /**
     * Pause the video.
     */
    public void pause() {
        if (currentVideoId == null) return;

        getElement().executeJs("""
            var player = window.ytPlayers[$0];
            if (player && player.pauseVideo) {
                player.pauseVideo();
            }
            """, playerId);

        isPlaying = false;
        updatePlayPauseButton();
    }

    private void updatePlayPauseButton() {
        if (isPlaying) {
            playPauseButton.setIcon(VaadinIcon.PAUSE.create());
        } else {
            playPauseButton.setIcon(VaadinIcon.PLAY.create());
        }
    }

    /**
     * Get the currently playing video ID.
     */
    public String getCurrentVideoId() {
        return currentVideoId;
    }

    /**
     * Get the currently playing video title.
     */
    public String getCurrentTitle() {
        return currentTitle;
    }

    /**
     * Check if a video is currently loaded.
     */
    public boolean hasVideo() {
        return currentVideoId != null;
    }
}
