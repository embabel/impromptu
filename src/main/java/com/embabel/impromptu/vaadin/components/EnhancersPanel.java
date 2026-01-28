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

import com.embabel.impromptu.data.GraphExportService;
import com.embabel.impromptu.data.pipeline.ComposerInfluenceLoader;
import com.embabel.impromptu.data.pipeline.ComposerEnhancementPipeline;
import com.embabel.impromptu.data.pipeline.ComposerNationalityEnhancer;
import com.embabel.impromptu.data.pipeline.ComposerTechniqueEnhancer;
import com.embabel.impromptu.data.pipeline.Enhancer;
import com.embabel.impromptu.data.pipeline.WorkInstrumentationEnhancer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel for managing composer enhancement pipeline.
 * Each enhancer has two phases: Generate (CSV) and Apply (to database).
 */
public class EnhancersPanel extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(EnhancersPanel.class);

    private final ComposerEnhancementPipeline pipeline;
    private final ComposerInfluenceLoader influenceLoader;
    private final ComposerTechniqueEnhancer techniqueEnhancer;
    private final ComposerNationalityEnhancer nationalityEnhancer;
    private final WorkInstrumentationEnhancer workInstrumentationEnhancer;
    private final GraphExportService exportService;

    public EnhancersPanel(
            ComposerEnhancementPipeline pipeline,
            ComposerInfluenceLoader influenceLoader,
            ComposerTechniqueEnhancer techniqueEnhancer,
            ComposerNationalityEnhancer nationalityEnhancer,
            WorkInstrumentationEnhancer workInstrumentationEnhancer,
            GraphExportService exportService) {
        this.pipeline = pipeline;
        this.influenceLoader = influenceLoader;
        this.techniqueEnhancer = techniqueEnhancer;
        this.nationalityEnhancer = nationalityEnhancer;
        this.workInstrumentationEnhancer = workInstrumentationEnhancer;
        this.exportService = exportService;

        setPadding(false);
        setSpacing(false);
        setSizeFull();

        // Description
        var description = new Div();
        description.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("line-height", "1.5");
        description.setText("Each enhancer has two phases: Generate (creates CSV for review) and Apply (loads approved data to database).");

        // Enhancers section
        var enhancersSection = createEnhancersSection();

        // Bulk operations section
        var bulkSection = createBulkSection();

        // Export section
        var exportSection = createExportSection();

        add(description, new Hr(), enhancersSection, new Hr(), bulkSection, new Hr(), exportSection);
    }

    private VerticalLayout createEnhancersSection() {
        var section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.setWidth("100%");

        var header = new H4("Enhancers");
        header.getStyle().set("margin", "0");
        section.add(header);

        for (var enhancer : pipeline.getAllEnhancers()) {
            section.add(createEnhancerRow(enhancer));
        }

        return section;
    }

    private HorizontalLayout createEnhancerRow(Enhancer enhancer) {
        var row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(Alignment.CENTER);
        row.getStyle()
                .set("padding", "var(--lumo-space-s)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        var info = new VerticalLayout();
        info.setPadding(false);
        info.setSpacing(false);

        var name = new Span(enhancer.getName());
        name.getStyle().set("font-weight", "500");

        var desc = new Span(enhancer.getDescription());
        desc.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        var path = new Span("CSV: " + enhancer.getOutputPath());
        path.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-family", "monospace")
                .set("color", "var(--lumo-tertiary-text-color)");
        info.add(name, desc, path);

        var generateButton = new Button("Generate", VaadinIcon.COG.create());
        generateButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        generateButton.getElement().setAttribute("title", "Generate CSV for review");
        generateButton.addClickListener(e -> generateEnhancer(enhancer));

        var applyButton = new Button("Apply", VaadinIcon.DATABASE.create());
        applyButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        applyButton.getElement().setAttribute("title", "Load approved CSV to database");
        applyButton.addClickListener(e -> applyEnhancer(enhancer));

        var buttons = new HorizontalLayout(generateButton, applyButton);
        buttons.setSpacing(true);

        row.add(info, buttons);
        row.setFlexGrow(1, info);

        return row;
    }

    private VerticalLayout createBulkSection() {
        var section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.setWidth("100%");

        var header = new H4("Bulk Operations");
        header.getStyle().set("margin", "0");

        var generateAllButton = new Button("Generate All CSVs", VaadinIcon.COG.create());
        generateAllButton.addClickListener(e -> generateAll());

        var applyAllButton = new Button("Apply All to Database", VaadinIcon.DATABASE.create());
        applyAllButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyAllButton.addClickListener(e -> applyAll(false));

        var forceApplyAllButton = new Button("Force Apply All", VaadinIcon.BOLT.create());
        forceApplyAllButton.getStyle().set("background-color", "#f59e0b");
        forceApplyAllButton.getStyle().set("color", "white");
        forceApplyAllButton.addClickListener(e -> applyAll(true));

        var deleteRelationshipsButton = new Button("Delete All Relationships", VaadinIcon.TRASH.create());
        deleteRelationshipsButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteRelationshipsButton.addClickListener(e -> deleteAllRelationships());

        var buttonRow = new HorizontalLayout(generateAllButton, applyAllButton, forceApplyAllButton, deleteRelationshipsButton);
        buttonRow.setSpacing(true);

        section.add(header, buttonRow);
        return section;
    }

    private VerticalLayout createExportSection() {
        var section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.setWidth("100%");

        var header = new H4("Export");
        header.getStyle().set("margin", "0");

        var exportButton = new Button("Export Cypher", VaadinIcon.UPLOAD.create());
        exportButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        exportButton.addClickListener(e -> exportCypher());

        section.add(header, exportButton);
        return section;
    }

    private void generateEnhancer(Enhancer enhancer) {
        var ui = getUI().orElse(null);
        Notification.show("Generating " + enhancer.getName() + "...", 2000, Notification.Position.BOTTOM_CENTER);

        new Thread(() -> {
            try {
                pipeline.generate(enhancer.getId());
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show(enhancer.getName() + " CSV generated!", 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to generate {}", enhancer.getId(), e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            }
        }).start();
    }

    private void applyEnhancer(Enhancer enhancer) {
        var ui = getUI().orElse(null);
        Notification.show("Applying " + enhancer.getName() + "...", 2000, Notification.Position.BOTTOM_CENTER);

        new Thread(() -> {
            try {
                var result = pipeline.apply(enhancer.getId());
                if (ui != null) {
                    ui.access(() -> {
                        var message = String.format("%s: %d loaded, %d skipped, %d failed",
                                enhancer.getName(), result.loaded(), result.skipped(), result.failed());
                        Notification.show(message, 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        refresh();
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to apply {}", enhancer.getId(), e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            }
        }).start();
    }

    private void generateAll() {
        var ui = getUI().orElse(null);
        Notification.show("Generating all CSVs...", 2000, Notification.Position.BOTTOM_CENTER);

        new Thread(() -> {
            try {
                pipeline.generateAll();
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("All CSVs generated!", 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to generate all", e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            }
        }).start();
    }

    private void applyAll(boolean ignoreStatus) {
        var ui = getUI().orElse(null);
        var message = ignoreStatus ? "Force applying all to database..." : "Applying all to database...";
        Notification.show(message, 2000, Notification.Position.BOTTOM_CENTER);

        new Thread(() -> {
            try {
                var results = pipeline.applyAll(ignoreStatus);
                if (ui != null) {
                    ui.access(() -> {
                        var total = results.stream().mapToInt(r -> r.result().loaded()).sum();
                        Notification.show("Applied all: " + total + " total loaded", 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        refresh();
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to apply all", e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            }
        }).start();
    }

    private void deleteAllRelationships() {
        var ui = getUI().orElse(null);

        new Thread(() -> {
            try {
                var influences = influenceLoader.deleteAllInfluences();
                var techniques = techniqueEnhancer.deleteAllUsesRelationships();
                var nationalities = nationalityEnhancer.deleteAllHasNationalityRelationships();
                var instrumentation = workInstrumentationEnhancer.deleteAllRelationships();
                var total = influences + techniques + nationalities + instrumentation;
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Deleted " + total + " relationships", 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        refresh();
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to delete relationships", e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            }
        }).start();
    }

    private void exportCypher() {
        var ui = getUI().orElse(null);

        new Thread(() -> {
            try {
                var result = exportService.exportReferences();
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show(
                                String.format("Exported %d nodes, %d relationships to %s",
                                        result.nodes(), result.relationships(), result.directory()),
                                5000, Notification.Position.BOTTOM_CENTER
                        ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to export graph", e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            }
        }).start();
    }

    public void refresh() {
        // No stats to refresh currently
    }
}
