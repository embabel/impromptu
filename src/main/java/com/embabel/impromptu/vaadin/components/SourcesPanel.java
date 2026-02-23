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

import com.embabel.impromptu.rag.DocumentService;
import com.embabel.vaadin.document.DocumentInfoProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sources panel showing documents/chunks with ingest controls.
 * Single consolidated view with stats and documents at top, ingest controls at bottom.
 */
public class SourcesPanel extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(SourcesPanel.class);

    private final DocumentService documentService;
    private final Span documentCountSpan;
    private final Span chunkCountSpan;
    private final VerticalLayout documentsList;
    private TextField urlField;
    private Button ingestButton;

    public SourcesPanel(DocumentService documentService) {
        this.documentService = documentService;

        setPadding(false);
        setSpacing(false);
        setSizeFull();

        // Stats row at top
        documentCountSpan = new Span("0");
        documentCountSpan.getStyle().set("font-weight", "500");
        chunkCountSpan = new Span("0");
        chunkCountSpan.getStyle().set("font-weight", "500");

        var statsRow = new HorizontalLayout();
        statsRow.setWidthFull();
        statsRow.setPadding(true);
        statsRow.setSpacing(true);
        statsRow.setAlignItems(Alignment.CENTER);
        statsRow.add(
                createStatBadge("Documents", documentCountSpan),
                createStatBadge("Chunks", chunkCountSpan)
        );

        // Documents list (scrollable)
        documentsList = new VerticalLayout();
        documentsList.setPadding(true);
        documentsList.setSpacing(false);

        var scroller = new Scroller(documentsList);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        // Ingest controls at bottom
        var ingestSection = createIngestSection();

        // Layout: stats at top, scrollable docs in middle, ingest at bottom
        add(statsRow, new Hr(), scroller, new Hr(), ingestSection);
        setFlexGrow(1, scroller);

        refresh();
    }

    private Div createStatBadge(String label, Span valueSpan) {
        var badge = new Div();
        badge.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-xs)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        var labelSpan = new Span(label + ":");
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        badge.add(labelSpan, valueSpan);
        return badge;
    }

    private VerticalLayout createIngestSection() {
        var section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.setWidth("100%");

        // URL input row
        urlField = new TextField();
        urlField.setPlaceholder("Enter URL to ingest...");
        urlField.setWidthFull();
        urlField.setClearButtonVisible(true);

        ingestButton = new Button(VaadinIcon.DOWNLOAD.create());
        ingestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ingestButton.addClickListener(e -> ingestUrl());

        var urlRow = new HorizontalLayout(urlField, ingestButton);
        urlRow.setWidthFull();
        urlRow.setFlexGrow(1, urlField);

        // File upload
        var buffer = new MemoryBuffer();
        var upload = new Upload(buffer);
        upload.setWidthFull();
        upload.setDropAllowed(true);
        upload.setAcceptedFileTypes(
                ".pdf", ".txt", ".md", ".html", ".htm",
                ".doc", ".docx", ".odt", ".rtf",
                "application/pdf", "text/plain", "text/markdown", "text/html",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
        upload.setMaxFileSize(10 * 1024 * 1024);

        upload.addSucceededListener(event -> {
            var filename = event.getFileName();
            try {
                var inputStream = buffer.getInputStream();
                var uri = "upload://" + filename;
                documentService.ingestStream(inputStream, uri, filename);

                Notification.show("Uploaded: " + filename, 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refresh();
            } catch (Exception e) {
                logger.error("Failed to ingest file: {}", filename, e);
                Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFailedListener(event -> {
            logger.error("Upload failed: {}", event.getReason().getMessage());
            Notification.show("Upload failed: " + event.getReason().getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        section.add(urlRow, upload);
        return section;
    }

    private void ingestUrl() {
        var url = urlField.getValue();
        if (url == null || url.trim().isEmpty()) {
            Notification.show("Please enter a URL", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        ingestButton.setEnabled(false);
        urlField.setEnabled(false);

        var finalUrl = url;
        var ui = getUI().orElse(null);

        new Thread(() -> {
            try {
                documentService.ingestUrl(finalUrl);

                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Ingested: " + finalUrl, 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        urlField.clear();
                        urlField.setEnabled(true);
                        ingestButton.setEnabled(true);
                        refresh();
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to ingest URL: {}", finalUrl, e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        urlField.setEnabled(true);
                        ingestButton.setEnabled(true);
                    });
                }
            }
        }).start();
    }

    public void refresh() {
        var formatter = java.text.NumberFormat.getNumberInstance();
        documentCountSpan.setText(formatter.format(documentService.getDocumentCount()));
        chunkCountSpan.setText(formatter.format(documentService.getChunkCount()));

        documentsList.removeAll();

        var documents = documentService.getDocuments();
        if (documents.isEmpty()) {
            var emptyLabel = new Span("No documents indexed");
            emptyLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");
            documentsList.add(emptyLabel);
        } else {
            for (var doc : documents) {
                documentsList.add(createDocumentRow(doc));
            }
        }
    }

    private HorizontalLayout createDocumentRow(DocumentInfoProvider.DocumentInfo doc) {
        var row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(Alignment.CENTER);
        row.setJustifyContentMode(JustifyContentMode.BETWEEN);
        row.getStyle()
                .set("padding", "var(--lumo-space-xs) 0")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        var title = new Span(doc.title() != null ? doc.title() : doc.uri());
        title.getStyle()
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");

        var deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        deleteButton.addClickListener(e -> {
            if (documentService.deleteDocument(doc.uri())) {
                Notification.show("Deleted: " + doc.title(), 3000, Notification.Position.BOTTOM_CENTER);
                refresh();
            } else {
                Notification.show("Failed to delete", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        row.add(title, deleteButton);
        row.setFlexGrow(1, title);

        return row;
    }
}
