/*
 * Copyright 2024-2025 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu.vaadin.components;

import com.embabel.chat.AssetView;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubePendingPlayback;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.proposition.persistence.DrivinePropositionRepository;
import com.embabel.impromptu.speech.PersonaService;
import com.embabel.impromptu.theme.ThemeService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.web.vaadin.components.AssetsPanel;
import com.embabel.web.vaadin.components.PropositionsPanel;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.embabel.agent.api.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Session panel showing user/conversation-specific content.
 * Opened by clicking on the user profile.
 */
public class SessionPanel extends Div {

    private static final Logger logger = LoggerFactory.getLogger(SessionPanel.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private ShortcutRegistration escapeShortcut;

    private final PropositionsPanel propositionsPanel;
    private YouTubePlayerPanel youTubePlayerPanel;
    private final Tabs tabs;
    private final Tab assetsTab;
    private final AssetsPanel assetsPanel;
    private final Config config;

    /**
     * Configuration for the session panel.
     */
    public record Config(
            ImpromptuUser user,
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            YouTubePendingPlayback youTubePendingPlayback,
            DrivinePropositionRepository propositionRepository,
            PersonaService personaService,
            ThemeService themeService,
            Consumer<EntityMention> onMentionClick,
            Supplier<AssetView> assetViewSupplier,
            Consumer<String> onPersonaChange,
            IntConsumer onMaxWordsChange,
            Consumer<String> onThemeChange,
            Runnable onAnalyze
    ) {
    }

    public SessionPanel(Config config) {
        this.config = config;
        addClassName("session-panel-container");

        // Backdrop for closing panel when clicking outside
        backdrop = new Div();
        backdrop.addClassName("side-panel-backdrop");
        backdrop.addClickListener(e -> close());

        // Side panel
        sidePanel = new VerticalLayout();
        sidePanel.addClassName("side-panel");
        sidePanel.addClassName("session-panel");
        sidePanel.setPadding(false);
        sidePanel.setSpacing(false);

        // Header with user info and close button
        var header = new HorizontalLayout();
        header.addClassName("side-panel-header");
        header.setWidthFull();

        var userInfo = new HorizontalLayout();
        userInfo.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        userInfo.setSpacing(true);

        // Avatar
        var initials = getInitials(config.user().getDisplayName());
        var avatar = new Div();
        avatar.setText(initials);
        avatar.getStyle()
                .set("width", "32px")
                .set("height", "32px")
                .set("border-radius", "50%")
                .set("background", "var(--lumo-primary-color)")
                .set("color", "var(--lumo-primary-contrast-color)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "600");

        var title = new Span(config.user().getDisplayName());
        title.addClassName("side-panel-title");

        userInfo.add(avatar, title);

        var closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("side-panel-close");
        closeButton.addClickListener(e -> close());

        header.add(userInfo, closeButton);
        header.setFlexGrow(1, userInfo);
        sidePanel.add(header);

        // Tabs
        var mediaTab = new Tab(VaadinIcon.MUSIC.create(), new Span("Media"));
        assetsTab = new Tab(VaadinIcon.CUBE.create(), new Span("Assets"));
        var memoryTab = new Tab(VaadinIcon.LIGHTBULB.create(), new Span("Memory"));
        var personalityTab = new Tab(VaadinIcon.AUTOMATION.create(), new Span("Personality"));
        var themeTab = new Tab(VaadinIcon.PAINT_ROLL.create(), new Span("Theme"));

        tabs = new Tabs(mediaTab, assetsTab, memoryTab, personalityTab, themeTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // Media content (default tab, so start visible)
        var mediaContent = createMediaContent(config);
        mediaContent.setVisible(true);

        // Assets content - with tool invoker for playback buttons
        assetsPanel = new AssetsPanel(config.assetViewSupplier(), tool -> invokeTool(tool, config));
        assetsPanel.setVisible(false);

        // Memory content (user propositions)
        var memoryContent = new VerticalLayout();
        memoryContent.setPadding(false);
        memoryContent.setVisible(false);

        var userContextId = config.user().currentContext();

        // Create propositions panel first (needed for button references)
        propositionsPanel = new PropositionsPanel(config.propositionRepository());
        propositionsPanel.setContextId(userContextId);
        propositionsPanel.setOnMentionClick(config.onMentionClick());
        propositionsPanel.setOnDelete(id -> config.propositionRepository().delete(id));

        // Button row with Analyze and Clear
        var buttonRow = new HorizontalLayout();
        buttonRow.setSpacing(true);
        buttonRow.addClassName("memory-button-row");

        var analyzeButton = new Button("Analyze Conversation", VaadinIcon.LIGHTBULB.create(), e -> config.onAnalyze().run());
        analyzeButton.addClassName("memory-analyze-button");
        analyzeButton.getElement().setAttribute("title", "Extract memories from conversation");

        var clearButton = new Button("Clear All", VaadinIcon.TRASH.create());
        clearButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        clearButton.addClassName("memory-clear-button");
        clearButton.getElement().setAttribute("title", "Delete all memories");
        clearButton.addClickListener(e -> {
            var dialog = new ConfirmDialog();
            dialog.setHeader("Clear Memories");
            dialog.setText("This will permanently delete ALL memories. This action cannot be undone.");
            dialog.setCancelable(true);
            dialog.setCloseOnEsc(true);
            dialog.setConfirmText("Delete All");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(event -> {
                config.propositionRepository().clearByContext(userContextId);
                propositionsPanel.refresh();
            });
            dialog.open();
        });

        buttonRow.add(analyzeButton, clearButton);
        memoryContent.add(buttonRow);
        memoryContent.add(propositionsPanel);

        // Personality content (persona selection)
        var personalityContent = new PersonalitySelectionPanel(config.personaService(), config.user(), config.onPersonaChange(), config.onMaxWordsChange());
        personalityContent.setVisible(false);

        // Theme content
        var themeContent = new ThemeSelectionPanel(config.themeService(), config.user(), config.onThemeChange());
        themeContent.setVisible(false);

        contentArea.add(mediaContent, assetsPanel, memoryContent, personalityContent, themeContent);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            mediaContent.setVisible(selected == mediaTab);
            assetsPanel.setVisible(selected == assetsTab);
            memoryContent.setVisible(selected == memoryTab);
            personalityContent.setVisible(selected == personalityTab);
            themeContent.setVisible(selected == themeTab);

            if (selected == assetsTab) {
                assetsPanel.refresh();
            }
            if (selected == memoryTab) {
                propositionsPanel.refresh();
            }
        });

        // Add elements
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(sidePanel.getElement());
    }

    private VerticalLayout createMediaContent(Config config) {
        var mediaContent = new VerticalLayout();
        mediaContent.setPadding(true);
        mediaContent.setSpacing(true);

        // Always show placeholder, replaced when services are linked
        var placeholder = new Span("No media services configured");
        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");
        mediaContent.add(placeholder);

        try {
            if (config.spotifyService() != null && config.spotifyService().isLinked(config.user())) {
                mediaContent.remove(placeholder);
                mediaContent.add(new SpotifyPlayerPanel(config.spotifyService(), config.user()));
            }
            if (config.youTubeService() != null && config.youTubeService().isConfigured()) {
                mediaContent.remove(placeholder);
                youTubePlayerPanel = new YouTubePlayerPanel(config.youTubeService());
                mediaContent.add(youTubePlayerPanel);
            }
        } catch (Exception e) {
            logger.warn("Error configuring media services: {}", e.getMessage());
        }

        return mediaContent;
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        var parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    public void open() {
        sidePanel.addClassName("open");
        backdrop.addClassName("visible");
        escapeShortcut = getUI().map(ui ->
                ui.addShortcutListener(this::close, Key.ESCAPE)
        ).orElse(null);
    }

    /**
     * Open the panel directly to the Assets tab.
     */
    public void openToAssets() {
        tabs.setSelectedTab(assetsTab);
        assetsPanel.refresh();
        open();
    }

    public void close() {
        sidePanel.removeClassName("open");
        backdrop.removeClassName("visible");
        if (escapeShortcut != null) {
            escapeShortcut.remove();
            escapeShortcut = null;
        }
    }

    /**
     * Invoke a tool from an asset and handle playback.
     */
    private void invokeTool(Tool tool, Config config) {
        try {
            var result = tool.call("{}");
            if (result instanceof Tool.Result.Text textResult) {
                var json = objectMapper.readTree(textResult.getContent());
                handlePlaybackJson(json, config);
            }
        } catch (Exception e) {
            logger.error("Failed to invoke tool: {}", tool.getDefinition().getName(), e);
        }
    }

    /**
     * Handle playback JSON from a tool result.
     */
    private void handlePlaybackJson(JsonNode json, Config config) {
        var source = json.path("source").asText();
        var type = json.path("type").asText();

        if ("concert".equals(type)) {
            // Concert - play all performances
            var performances = json.path("performances");
            var platform = json.path("platform").asText();
            if (performances.isArray() && !performances.isEmpty()) {
                if ("spotify".equals(platform)) {
                    // Collect all Spotify track URIs from all performances
                    List<String> allUris = new java.util.ArrayList<>();
                    for (var perf : performances) {
                        var trackUris = perf.path("trackUris");
                        if (trackUris.isArray()) {
                            for (var uri : trackUris) {
                                allUris.add(uri.asText());
                            }
                        }
                    }
                    if (!allUris.isEmpty() && config.user().isSpotifyLinked()) {
                        try {
                            config.spotifyService().play(config.user(), null, allUris);
                            logger.info("Playing Spotify concert ({} tracks): {}",
                                    allUris.size(), json.path("title").asText());
                        } catch (Exception e) {
                            logger.error("Failed to play Spotify concert", e);
                        }
                    }
                } else if ("youtube".equals(platform)) {
                    // Collect all video IDs and open as YouTube playlist
                    List<String> videoIds = new java.util.ArrayList<>();
                    for (var perf : performances) {
                        var videoId = perf.path("videoId").asText();
                        if (videoId.isBlank()) {
                            videoId = perf.path("id").asText();
                        }
                        if (!videoId.isBlank()) {
                            videoIds.add(videoId);
                        }
                    }
                    if (!videoIds.isEmpty()) {
                        openYouTubePlaylist(videoIds, json.path("title").asText());
                    }
                }
            }
        } else if ("spotify".equals(source)) {
            if (config.user().isSpotifyLinked()) {
                try {
                    List<String> uris = new java.util.ArrayList<>();

                    // First check for trackUris array (from SpotifyPerformance)
                    var trackUrisNode = json.path("trackUris");
                    if (trackUrisNode.isArray() && !trackUrisNode.isEmpty()) {
                        for (var uriNode : trackUrisNode) {
                            uris.add(uriNode.asText());
                        }
                    }

                    // Fall back to single uri (from SpotifyTrackDetails)
                    if (uris.isEmpty()) {
                        var uri = json.path("uri").asText();
                        if (!uri.isBlank()) {
                            uris.add(uri);
                        }
                    }

                    // Fall back to constructing from id
                    if (uris.isEmpty()) {
                        var id = json.path("id").asText();
                        if (!id.isBlank() && id.startsWith("spotify:")) {
                            uris.add(id);
                        }
                    }

                    if (!uris.isEmpty()) {
                        config.spotifyService().play(config.user(), null, uris);
                        logger.info("Playing Spotify ({}): {}", uris.size(), json.path("title").asText());
                    } else {
                        logger.warn("No Spotify URIs found in playback JSON: {}", json);
                    }
                } catch (Exception e) {
                    logger.error("Failed to play Spotify track", e);
                }
            }
        } else if ("youtube".equals(source)) {
            var videoId = json.path("id").asText();
            var title = json.path("title").asText();
            if (!videoId.isBlank()) {
                // Queue for YouTube player
                config.youTubePendingPlayback().requestPlayback(
                        config.user().getId(), videoId, title, "");
                if (youTubePlayerPanel != null) {
                    youTubePlayerPanel.loadVideo(videoId, title, "");
                }
                logger.info("Playing YouTube video: {}", title);
            }
        }
    }

    /**
     * Open multiple YouTube videos as a playlist.
     */
    private void openYouTubePlaylist(List<String> videoIds, String title) {
        if (videoIds.isEmpty()) return;

        // Use YouTube's watch_videos feature to create a temporary playlist
        String ids = String.join(",", videoIds);
        String url = "https://www.youtube.com/watch_videos?video_ids=" + ids;

        // Update the player panel with first video info
        if (youTubePlayerPanel != null) {
            youTubePlayerPanel.loadVideo(videoIds.getFirst(), title + " (playlist)", "");
        }

        // Open playlist in popup
        getUI().ifPresent(ui -> ui.getPage().executeJs("""
                        var w = Math.round(screen.width * 0.6);
                        var h = Math.round(screen.height * 0.6);
                        var left = Math.round((screen.width - w) / 2);
                        var top = Math.round((screen.height - h) / 2);
                        window.open($0, 'youtube_player',
                            'width=' + w + ',height=' + h + ',left=' + left + ',top=' + top +
                            ',menubar=no,toolbar=no,location=no,status=no,resizable=yes');
                        """,
                url
        ));
        logger.info("Opening YouTube playlist ({} videos): {}", videoIds.size(), title);
    }

    public boolean isOpen() {
        return sidePanel.hasClassName("open");
    }

    public PropositionsPanel getPropositionsPanel() {
        return propositionsPanel;
    }

    public YouTubePlayerPanel getYouTubePlayerPanel() {
        return youTubePlayerPanel;
    }

    public AssetsPanel getAssetsPanel() {
        return assetsPanel;
    }

    /**
     * Invoke a tool and handle playback. Used by inline asset cards.
     */
    public void invokeToolFromAsset(Tool tool) {
        invokeTool(tool, config);
    }
}
