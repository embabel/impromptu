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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

/**
 * Knowledge panel with tabs for ingesting documents and viewing the document list.
 */
public class KnowledgePanel extends VerticalLayout {

    private final DocumentListSection documentsSection;

    public KnowledgePanel(DocumentService documentService) {
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        // Create tabs
        var uploadTab = new Tab(VaadinIcon.UPLOAD.create(), new Span("Upload"));
        var urlTab = new Tab(VaadinIcon.GLOBE.create(), new Span("URL"));
        var htmlTab = new Tab(VaadinIcon.CODE.create(), new Span("HTML"));
        var documentsTab = new Tab(VaadinIcon.FILE_TEXT.create(), new Span("Documents"));

        var tabs = new Tabs(uploadTab, urlTab, htmlTab, documentsTab);
        tabs.setWidthFull();
        tabs.addClassName("knowledge-tabs");

        // Create sections
        documentsSection = new DocumentListSection(documentService, () -> {});
        var uploadSection = new FileUploadSection(documentService, this::refreshDocuments);
        var urlSection = new UrlIngestSection(documentService, this::refreshDocuments);
        var htmlSection = new HtmlIngestSection(documentService, this::refreshDocuments);

        // Default visibility
        urlSection.setVisible(false);
        htmlSection.setVisible(false);
        documentsSection.setVisible(false);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            uploadSection.setVisible(event.getSelectedTab() == uploadTab);
            urlSection.setVisible(event.getSelectedTab() == urlTab);
            htmlSection.setVisible(event.getSelectedTab() == htmlTab);
            documentsSection.setVisible(event.getSelectedTab() == documentsTab);
            if (event.getSelectedTab() == documentsTab) {
                documentsSection.refresh();
            }
        });

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.setPadding(false);
        contentArea.setSpacing(false);
        contentArea.setSizeFull();
        contentArea.add(uploadSection, urlSection, htmlSection, documentsSection);

        add(tabs, contentArea);
        setFlexGrow(1, contentArea);
    }

    private void refreshDocuments() {
        documentsSection.refresh();
    }

    public void refresh() {
        documentsSection.refresh();
    }
}
