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
package com.embabel.web.vaadin.components;

import com.embabel.agent.api.tool.Tool;
import com.embabel.chat.Asset;
import com.embabel.chat.AssetView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel displaying conversation assets (tools, references, etc.).
 */
public class AssetsPanel extends VerticalLayout {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Supplier<AssetView> assetViewSupplier;
    private final Consumer<Tool> toolInvoker;
    private final VerticalLayout assetsList;
    private final Span emptyMessage;

    public AssetsPanel(Supplier<AssetView> assetViewSupplier) {
        this(assetViewSupplier, null);
    }

    public AssetsPanel(Supplier<AssetView> assetViewSupplier, Consumer<Tool> toolInvoker) {
        this.assetViewSupplier = assetViewSupplier;
        this.toolInvoker = toolInvoker;
        setPadding(true);
        setSpacing(true);
        setSizeFull();

        emptyMessage = new Span("No assets yet. Assets are added when tools produce outputs.");
        emptyMessage.addClassName("empty-message");

        assetsList = new VerticalLayout();
        assetsList.setPadding(false);
        assetsList.setSpacing(true);

        add(emptyMessage, assetsList);
        refresh();
    }

    public void refresh() {
        assetsList.removeAll();

        var assetView = assetViewSupplier.get();
        if (assetView == null || assetView.getAssets().isEmpty()) {
            emptyMessage.setVisible(true);
            assetsList.setVisible(false);
            return;
        }

        emptyMessage.setVisible(false);
        assetsList.setVisible(true);

        for (var asset : assetView.getAssets()) {
            assetsList.add(createAssetCard(asset));
        }
    }

    private Div createAssetCard(Asset asset) {
        var card = new Div();
        card.addClassName("asset-card");

        var reference = asset.reference();

        // Name
        var name = new Span(reference.getName());
        name.addClassName("asset-name");

        // Description
        var description = new Span(reference.getDescription());
        description.addClassName("asset-description");

        // Timestamp
        var timestamp = asset.getTimestamp()
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMAT);
        var time = new Span(timestamp);
        time.addClassName("asset-time");

        // Notes (if available)
        var notes = reference.notes();
        Span notesSpan = null;
        if (notes != null && !notes.isBlank()) {
            notesSpan = new Span(notes);
            notesSpan.addClassName("asset-notes");
        }

        var content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);

        var header = new Div();
        header.addClassName("asset-header");
        header.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("gap", "1em");
        header.add(name, time);

        content.add(header, description);
        if (notesSpan != null) {
            content.add(notesSpan);
        }

        // Add buttons for no-arg tools
        var tools = reference.tools();
        if (tools != null && !tools.isEmpty() && toolInvoker != null) {
            var buttonsLayout = new FlexLayout();
            buttonsLayout.addClassName("asset-tools");
            buttonsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            buttonsLayout.getStyle().set("gap", "0.5em");

            for (var tool : tools) {
                if (isNoArgTool(tool)) {
                    var button = new Button(tool.getDefinition().getName());
                    button.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    button.addClassName("asset-tool-button");
                    button.addClickListener(e -> toolInvoker.accept(tool));
                    buttonsLayout.add(button);
                }
            }

            if (buttonsLayout.getComponentCount() > 0) {
                content.add(buttonsLayout);
            }
        }

        card.add(content);
        return card;
    }

    /**
     * Check if tool has no required parameters.
     */
    private boolean isNoArgTool(Tool tool) {
        var definition = tool.getDefinition();
        if (definition == null) {
            return true;
        }
        var schema = definition.getInputSchema();
        if (schema == null) {
            return true;
        }
        var params = schema.getParameters();
        if (params == null || params.isEmpty()) {
            return true;
        }
        // Check if all parameters are optional
        return params.stream().noneMatch(Tool.Parameter::getRequired);
    }
}
