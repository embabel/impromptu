package com.embabel.impromptu.vaadin.components;

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.rag.DocumentService;
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

/**
 * Backstage panel showing app-level content: References, Sources, Settings, About.
 * Session-level content (Media, Assets, Memory, Voice) is now in SessionPanel.
 */
public class BackstagePanel extends Div {

    private static final Logger logger = LoggerFactory.getLogger(BackstagePanel.class);

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private final Button toggleButton;
    private ShortcutRegistration escapeShortcut;

    /**
     * Configuration for the backstage panel.
     */
    public record Config(
            NamedEntityDataRepository entityRepository,
            DocumentService documentService,
            ReferencesPanel.IndexStats indexStats,
            ImpromptuProperties properties
    ) {
    }

    public BackstagePanel(Config config) {
        // Backdrop for closing panel when clicking outside
        backdrop = new Div();
        backdrop.addClassName("side-panel-backdrop");
        backdrop.addClickListener(e -> close());

        // Toggle button on right edge
        toggleButton = new Button(VaadinIcon.ELLIPSIS_DOTS_V.create());
        toggleButton.addClassName("side-panel-toggle");
        toggleButton.getElement().setAttribute("title", "Impromptu");
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

        var title = new Span("Impromptu");
        title.addClassName("side-panel-title");

        var closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("side-panel-close");
        closeButton.addClickListener(e -> close());

        header.add(title, closeButton);
        header.setFlexGrow(1, title);
        sidePanel.add(header);

        // Definition (always visible)
        var definition = new Div();
        definition.getStyle()
                .set("font-style", "italic")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "var(--lumo-space-m)")
                .set("font-size", "1.1rem")
                .set("line-height", "1.5")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        definition.setText("impromptu (noun): a short piece of instrumental music, especially a solo, that is reminiscent of an improvisation.");
        sidePanel.add(definition);

        // App tabs
        var referencesTab = new Tab(VaadinIcon.RECORDS.create(), new Span("References"));
        var sourcesTab = new Tab(VaadinIcon.BOOK.create(), new Span("Sources"));
        var settingsTab = new Tab(VaadinIcon.COG.create(), new Span("Settings"));
        var aboutTab = new Tab(VaadinIcon.INFO_CIRCLE.create(), new Span("About"));

        var tabs = new Tabs(referencesTab, sourcesTab, settingsTab, aboutTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // References content
        var referencesContent = new ReferencesPanel(config.entityRepository(), config.indexStats());

        // Sources content (documents)
        var sourcesContent = new SourcesPanel(config.documentService());
        sourcesContent.setVisible(false);

        // Settings content
        var settingsContent = new SettingsPanel(config.properties());
        settingsContent.setVisible(false);

        // About content
        var aboutContent = new AboutPanel();

        contentArea.add(referencesContent, sourcesContent, settingsContent, aboutContent);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            referencesContent.setVisible(selected == referencesTab);
            sourcesContent.setVisible(selected == sourcesTab);
            settingsContent.setVisible(selected == settingsTab);
            aboutContent.setVisible(selected == aboutTab);

            if (selected == sourcesTab) {
                sourcesContent.refresh();
            }
        });

        // Add elements
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(toggleButton.getElement());
        getElement().appendChild(sidePanel.getElement());
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

    public boolean isOpen() {
        return sidePanel.hasClassName("open");
    }
}
