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

import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.web.vaadin.components.LlmOptionsPanel;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Settings panel showing current LLM and embedding configuration.
 */
public class SettingsPanel extends VerticalLayout {

    public SettingsPanel(ImpromptuProperties properties) {
        setPadding(true);
        setSpacing(true);
        setVisible(false);

        // Section header
        var header = new Span("Model Configuration");
        header.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "bold");
        add(header);

        add(new Hr());

        // Embedding service
        var embeddingLabel = new Span("Embedding Service");
        embeddingLabel.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        add(embeddingLabel);

        var embeddingValue = new Span(properties.embeddingService());
        embeddingValue.getStyle()
                .set("font-size", "var(--lumo-font-size-m)")
                .set("font-family", "var(--lumo-font-family-monospace, monospace)");
        add(embeddingValue);

        add(new Hr());

        // Chat LLM
        if (properties.chatLlm() != null) {
            add(new LlmOptionsPanel("Chat", properties.chatLlm()));
            add(new Hr());
        }

        // Cypher Generation LLM
        if (properties.cypherGenerationLlm() != null) {
            add(new LlmOptionsPanel("Cypher Generation", properties.cypherGenerationLlm()));
            add(new Hr());
        }

        // Proposition Extraction LLM
        if (properties.propositionExtractionLlm() != null) {
            add(new LlmOptionsPanel("Proposition Extraction", properties.propositionExtractionLlm()));
            add(new Hr());
        }

        // Entity Resolution LLM
        if (properties.entityResolutionLlm() != null) {
            add(new LlmOptionsPanel("Entity Resolution", properties.entityResolutionLlm()));
        }
    }
}
