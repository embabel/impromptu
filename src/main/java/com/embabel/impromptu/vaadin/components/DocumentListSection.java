/*
 * Copyright 2024-2025 Embabel Software, Inc.
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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Document list section showing indexed documents with statistics.
 */
public class DocumentListSection extends VerticalLayout {

    private final DocumentService documentService;
    private final Runnable onDocumentsChanged;
    private final VerticalLayout documentsList;
    private final Span documentCountSpan;
    private final Span chunkCountSpan;

    public DocumentListSection(DocumentService documentService, Runnable onDocumentsChanged) {
        this.documentService = documentService;
        this.onDocumentsChanged = onDocumentsChanged;

        setPadding(true);
        setSpacing(true);

        // Stats section
        var statsTitle = new H4("Statistics");
        statsTitle.addClassName("section-title");

        var statsContainer = new Div();
        statsContainer.addClassName("stats-container");

        documentCountSpan = new Span();
        documentCountSpan.addClassName("stat-value");

        chunkCountSpan = new Span();
        chunkCountSpan.addClassName("stat-value");

        statsContainer.add(createStatRow("Documents", documentCountSpan), createStatRow("Chunks", chunkCountSpan));

        // Documents list section
        var docsTitle = new H4("Documents");
        docsTitle.addClassName("section-title");
        docsTitle.getStyle().set("margin-top", "var(--lumo-space-m)");

        documentsList = new VerticalLayout();
        documentsList.setPadding(false);
        documentsList.setSpacing(false);
        documentsList.addClassName("documents-list");

        add(statsTitle, statsContainer, docsTitle, documentsList);

        refresh();
    }

    private Div createStatRow(String label, Span valueSpan) {
        var row = new Div();
        row.addClassName("stat-row");

        var labelSpan = new Span(label);
        labelSpan.addClassName("stat-label");

        row.add(labelSpan, valueSpan);
        return row;
    }

    public void refresh() {
        documentCountSpan.setText(String.valueOf(documentService.getDocumentCount()));
        chunkCountSpan.setText(String.valueOf(documentService.getChunkCount()));

        documentsList.removeAll();

        var documents = documentService.getDocuments();
        if (documents.isEmpty()) {
            var emptyLabel = new Span("No documents indexed yet");
            emptyLabel.addClassName("empty-list-label");
            documentsList.add(emptyLabel);
        } else {
            for (var doc : documents) {
                documentsList.add(createDocumentRow(doc));
            }
        }
    }

    private HorizontalLayout createDocumentRow(DocumentService.DocumentInfo doc) {
        var row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(Alignment.CENTER);
        row.addClassName("document-row");

        var infoSection = new VerticalLayout();
        infoSection.setPadding(false);
        infoSection.setSpacing(false);

        var title = new Span(doc.title() != null ? doc.title() : doc.uri());
        title.addClassName("document-title");

        infoSection.add(title);

        var deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        deleteButton.addClickListener(e -> {
            if (documentService.deleteDocument(doc.uri())) {
                Notification.show("Deleted: " + doc.title(), 3000, Notification.Position.BOTTOM_CENTER);
                refresh();
                onDocumentsChanged.run();
            } else {
                Notification.show("Failed to delete", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        row.add(infoSection, deleteButton);
        row.setFlexGrow(1, infoSection);

        return row;
    }
}
