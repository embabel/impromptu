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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTML ingestion section for directly pasting HTML content.
 */
public class HtmlIngestSection extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(HtmlIngestSection.class);

    private final TextField titleField;
    private final TextArea htmlArea;
    private final Button ingestButton;

    public HtmlIngestSection(DocumentService documentService, Runnable onSuccess) {
        setPadding(true);
        setSpacing(true);

        var instructions = new Span("Paste HTML content to index directly");
        instructions.addClassName("section-instructions");

        titleField = new TextField("Title");
        titleField.setPlaceholder("Document title");
        titleField.setWidthFull();
        titleField.setClearButtonVisible(true);

        htmlArea = new TextArea("HTML Content");
        htmlArea.setPlaceholder("<html>...</html> or just text content");
        htmlArea.setWidthFull();
        htmlArea.setMinHeight("150px");
        htmlArea.setMaxHeight("300px");

        ingestButton = new Button("Ingest HTML", VaadinIcon.CODE.create());
        ingestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ingestButton.addClickListener(e -> ingestHtml(documentService, onSuccess));

        var buttonRow = new HorizontalLayout(ingestButton);
        buttonRow.setWidthFull();
        buttonRow.setJustifyContentMode(JustifyContentMode.END);

        add(instructions, titleField, htmlArea, buttonRow);
    }

    private void ingestHtml(DocumentService documentService, Runnable onSuccess) {
        var title = titleField.getValue();
        var html = htmlArea.getValue();

        if (html == null || html.trim().isEmpty()) {
            Notification.show("Please enter HTML content", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        if (title == null || title.trim().isEmpty()) {
            title = "Untitled HTML";
        }

        ingestButton.setEnabled(false);
        titleField.setEnabled(false);
        htmlArea.setEnabled(false);

        var finalTitle = title.trim();
        var finalHtml = html.trim();
        var ui = getUI().orElse(null);

        new Thread(() -> {
            try {
                documentService.ingestHtml(finalHtml, finalTitle);

                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Ingested: " + finalTitle, 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        titleField.clear();
                        htmlArea.clear();
                        titleField.setEnabled(true);
                        htmlArea.setEnabled(true);
                        ingestButton.setEnabled(true);
                        onSuccess.run();
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to ingest HTML: {}", finalTitle, e);
                if (ui != null) {
                    ui.access(() -> {
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        titleField.setEnabled(true);
                        htmlArea.setEnabled(true);
                        ingestButton.setEnabled(true);
                    });
                }
            }
        }).start();
    }
}
