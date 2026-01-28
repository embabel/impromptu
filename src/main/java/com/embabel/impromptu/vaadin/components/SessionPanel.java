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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Session panel showing user/conversation-specific content.
 * Opened by clicking on the user profile.
 */
public class SessionPanel extends Div {

    private static final Logger logger = LoggerFactory.getLogger(SessionPanel.class);

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private ShortcutRegistration escapeShortcut;

    private final PropositionsPanel propositionsPanel;
    private YouTubePlayerPanel youTubePlayerPanel;

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
            Consumer<String> onThemeChange,
            Runnable onAnalyze
    ) {
    }

    public SessionPanel(Config config) {
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
        var assetsTab = new Tab(VaadinIcon.CUBE.create(), new Span("Assets"));
        var memoryTab = new Tab(VaadinIcon.LIGHTBULB.create(), new Span("Memory"));
        var voiceTab = new Tab(VaadinIcon.USER.create(), new Span("Voice"));
        var themeTab = new Tab(VaadinIcon.PAINT_ROLL.create(), new Span("Theme"));

        var tabs = new Tabs(mediaTab, assetsTab, memoryTab, voiceTab, themeTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // Media content
        var mediaContent = createMediaContent(config);

        // Assets content
        var assetsContent = new AssetsPanel(config.assetViewSupplier());
        assetsContent.setVisible(false);

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

        // Voice content (persona selection)
        var voiceContent = new VoiceSelectionPanel(config.personaService(), config.user(), config.onPersonaChange());
        voiceContent.setVisible(false);

        // Theme content
        var themeContent = new ThemeSelectionPanel(config.themeService(), config.user(), config.onThemeChange());
        themeContent.setVisible(false);

        contentArea.add(mediaContent, assetsContent, memoryContent, voiceContent, themeContent);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            mediaContent.setVisible(selected == mediaTab);
            assetsContent.setVisible(selected == assetsTab);
            memoryContent.setVisible(selected == memoryTab);
            voiceContent.setVisible(selected == voiceTab);
            themeContent.setVisible(selected == themeTab);

            if (selected == assetsTab) {
                assetsContent.refresh();
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
        mediaContent.setPadding(false);
        mediaContent.setSpacing(true);

        if (config.spotifyService().isLinked(config.user())) {
            mediaContent.add(new SpotifyPlayerPanel(config.spotifyService(), config.user()));
        }
        if (config.youTubeService().isConfigured()) {
            youTubePlayerPanel = new YouTubePlayerPanel(config.youTubeService());
            mediaContent.add(youTubePlayerPanel);
        }
        if (mediaContent.getComponentCount() == 0) {
            mediaContent.add(new Span("No media services configured"));
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

    public void close() {
        sidePanel.removeClassName("open");
        backdrop.removeClassName("visible");
        if (escapeShortcut != null) {
            escapeShortcut.remove();
            escapeShortcut = null;
        }
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
}
