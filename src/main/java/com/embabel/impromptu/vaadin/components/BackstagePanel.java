/*
 * Copyright 2024-2026 Embabel Pty Ltd.
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

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.data.GraphExportService;
import com.embabel.impromptu.data.pipeline.ComposerInfluenceLoader;
import com.embabel.impromptu.data.pipeline.ComposerEnhancementPipeline;
import com.embabel.impromptu.data.pipeline.ComposerNationalityEnhancer;
import com.embabel.impromptu.data.pipeline.ComposerTechniqueEnhancer;
import com.embabel.impromptu.data.pipeline.WorkInstrumentationEnhancer;
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
            ImpromptuProperties properties,
            ComposerEnhancementPipeline pipeline,
            ComposerInfluenceLoader influenceLoader,
            ComposerTechniqueEnhancer techniqueEnhancer,
            ComposerNationalityEnhancer nationalityEnhancer,
            WorkInstrumentationEnhancer workInstrumentationEnhancer,
            GraphExportService exportService
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
                .set("font-size", "1.3rem")
                .set("line-height", "1.5")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        definition.setText("impromptu (noun): a short piece of instrumental music, especially a solo, that is reminiscent of an improvisation.");
        sidePanel.add(definition);

        // App tabs
        var referencesTab = new Tab(VaadinIcon.RECORDS.create(), new Span("Composers"));
        var sourcesTab = new Tab(VaadinIcon.BOOK.create(), new Span("Sources"));
        var enhancersTab = new Tab(VaadinIcon.MAGIC.create(), new Span("Enhancers"));
        var settingsTab = new Tab(VaadinIcon.COG.create(), new Span("Settings"));
        var aboutTab = new Tab(VaadinIcon.INFO_CIRCLE.create(), new Span("About"));

        var tabs = new Tabs(referencesTab, sourcesTab, enhancersTab, settingsTab, aboutTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // References content (default tab, so start visible)
        var referencesContent = new ReferencesPanel(config.entityRepository());
        referencesContent.setVisible(true);

        // Sources content (documents)
        var sourcesContent = new SourcesPanel(config.documentService());
        sourcesContent.setVisible(false);

        // Enhancers content
        var enhancersContent = new EnhancersPanel(config.pipeline(), config.influenceLoader(), config.techniqueEnhancer(), config.nationalityEnhancer(), config.workInstrumentationEnhancer(), config.exportService());
        enhancersContent.setVisible(false);

        // Settings content
        var settingsContent = new SettingsPanel(config.properties());
        settingsContent.setVisible(false);

        // About content
        var aboutContent = new AboutPanel();

        contentArea.add(referencesContent, sourcesContent, enhancersContent, settingsContent, aboutContent);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            referencesContent.setVisible(selected == referencesTab);
            sourcesContent.setVisible(selected == sourcesTab);
            enhancersContent.setVisible(selected == enhancersTab);
            settingsContent.setVisible(selected == settingsTab);
            aboutContent.setVisible(selected == aboutTab);

            if (selected == sourcesTab) {
                sourcesContent.refresh();
            } else if (selected == enhancersTab) {
                enhancersContent.refresh();
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
