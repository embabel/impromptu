package com.embabel.impromptu.vaadin.components;

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.impromptu.proposition.persistence.DrivinePropositionRepository;
import com.embabel.impromptu.spotify.SpotifyService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.impromptu.user.ImpromptuUserService;
import com.embabel.impromptu.voice.PersonaService;
import com.embabel.impromptu.youtube.YouTubeService;
import com.embabel.web.vaadin.components.PropositionsPanel;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
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

/**
 * Backstage panel component with tabs for Media, Library, Knowledge, About, and Settings.
 */
public class BackstagePanel extends Div {

    private static final Logger logger = LoggerFactory.getLogger(BackstagePanel.class);

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private final Button toggleButton;
    private ShortcutRegistration escapeShortcut;

    private final PropositionsPanel propositionsPanel;
    private YouTubePlayerPanel youTubePlayerPanel;

    /**
     * Configuration for the backstage panel.
     */
    public record Config(
            ImpromptuUser user,
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            NamedEntityDataRepository entityRepository,
            DrivinePropositionRepository propositionRepository,
            PersonaService personaService,
            ImpromptuUserService userService,
            Consumer<EntityMention> onMentionClick,
            ReferencesPanel.IndexStats indexStats
    ) {}

    public BackstagePanel(Config config) {
        // Backdrop for closing panel when clicking outside
        backdrop = new Div();
        backdrop.addClassName("side-panel-backdrop");
        backdrop.addClickListener(e -> close());

        // Toggle button on right edge
        toggleButton = new Button(VaadinIcon.ELLIPSIS_DOTS_V.create());
        toggleButton.addClassName("side-panel-toggle");
        toggleButton.getElement().setAttribute("title", "Backstage");
        toggleButton.addClickListener(e -> open());

        // Side panel
        sidePanel = new VerticalLayout();
        sidePanel.addClassName("side-panel");
        sidePanel.setPadding(false);
        sidePanel.setSpacing(false);

        // Header with close button
        var header = new HorizontalLayout();
        header.addClassName("side-panel-header");
        header.setWidthFull();

        var title = new Span("Backstage");
        title.addClassName("side-panel-title");

        var closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("side-panel-close");
        closeButton.addClickListener(e -> close());

        header.add(title, closeButton);
        header.setFlexGrow(1, title);
        sidePanel.add(header);

        // Tabs
        var mediaTab = new Tab(VaadinIcon.MUSIC.create(), new Span("Media"));
        var referencesTab = new Tab(VaadinIcon.RECORDS.create(), new Span("References"));
        var knowledgeTab = new Tab(VaadinIcon.BOOK.create(), new Span("Knowledge"));
        var settingsTab = new Tab(VaadinIcon.COG.create(), new Span("Settings"));
        var aboutTab = new Tab(VaadinIcon.INFO_CIRCLE.create(), new Span("About"));

        var tabs = new Tabs(mediaTab, referencesTab, knowledgeTab, settingsTab, aboutTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // Media content
        var mediaContent = createMediaContent(config);

        // References content
        var referencesContent = new ReferencesPanel(config.entityRepository(), config.indexStats());

        // Knowledge content
        var knowledgeContent = new VerticalLayout();
        knowledgeContent.setPadding(false);
        knowledgeContent.setVisible(false);

        var userContextId = config.user().currentContext();
        propositionsPanel = new PropositionsPanel(config.propositionRepository());
        propositionsPanel.setContextId(userContextId);
        propositionsPanel.setOnMentionClick(config.onMentionClick());
        propositionsPanel.setOnClear(() -> config.propositionRepository().clearByContext(userContextId));
        knowledgeContent.add(propositionsPanel);

        // About content
        var aboutContent = new AboutPanel();

        // Settings content
        var settingsContent = createSettingsContent();

        contentArea.add(mediaContent, referencesContent, knowledgeContent, settingsContent, aboutContent);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            mediaContent.setVisible(event.getSelectedTab() == mediaTab);
            referencesContent.setVisible(event.getSelectedTab() == referencesTab);
            knowledgeContent.setVisible(event.getSelectedTab() == knowledgeTab);
            settingsContent.setVisible(event.getSelectedTab() == settingsTab);
            aboutContent.setVisible(event.getSelectedTab() == aboutTab);
            if (event.getSelectedTab() == knowledgeTab) {
                propositionsPanel.refresh();
            }
        });

        // Add elements
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(toggleButton.getElement());
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

    private VerticalLayout createSettingsContent() {
        var content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setVisible(false);

        var placeholder = new Span("General settings coming soon...");
        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");
        content.add(placeholder);

        return content;
    }

    public void open() {
        sidePanel.addClassName("open");
        backdrop.addClassName("visible");
        toggleButton.addClassName("hidden");
        escapeShortcut = getUI().map(ui ->
                ui.addShortcutListener(this::close, Key.ESCAPE)
        ).orElse(null);
    }

    public void close() {
        sidePanel.removeClassName("open");
        backdrop.removeClassName("visible");
        toggleButton.removeClassName("hidden");
        if (escapeShortcut != null) {
            escapeShortcut.remove();
            escapeShortcut = null;
        }
    }

    public PropositionsPanel getPropositionsPanel() {
        return propositionsPanel;
    }

    public YouTubePlayerPanel getYouTubePlayerPanel() {
        return youTubePlayerPanel;
    }
}
