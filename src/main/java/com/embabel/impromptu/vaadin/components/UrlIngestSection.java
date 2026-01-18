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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * URL ingestion section for fetching and indexing web content.
 */
public class UrlIngestSection extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(UrlIngestSection.class);

    private final TextField urlField;
    private final Button ingestButton;

    public UrlIngestSection(DocumentService documentService, Runnable onSuccess) {
        setPadding(true);
        setSpacing(true);

        var instructions = new Span("Enter a URL to fetch and index its content");
        instructions.addClassName("section-instructions");

        urlField = new TextField();
        urlField.setPlaceholder("https://example.com/page");
        urlField.setWidthFull();
        urlField.setClearButtonVisible(true);

        ingestButton = new Button("Ingest", VaadinIcon.DOWNLOAD.create());
        ingestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ingestButton.addClickListener(e -> ingestUrl(documentService, onSuccess));

        var inputRow = new HorizontalLayout(urlField, ingestButton);
        inputRow.setWidthFull();
        inputRow.setFlexGrow(1, urlField);

        add(instructions, inputRow);
    }

    private void ingestUrl(DocumentService documentService, Runnable onSuccess) {
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
                        onSuccess.run();
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
}
