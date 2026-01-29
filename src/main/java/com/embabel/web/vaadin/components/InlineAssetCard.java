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
package com.embabel.web.vaadin.components;

import com.embabel.agent.api.tool.Tool;
import com.embabel.chat.Asset;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;

import java.util.function.Consumer;

/**
 * Compact inline card displayed in the conversation when a new asset is created.
 * Shows the asset name with action buttons for playback and navigation to the Assets tab.
 */
public class InlineAssetCard extends Div {

    public InlineAssetCard(Asset asset, Consumer<Tool> toolInvoker, Runnable onViewInAssets) {
        addClassName("inline-asset-card");

        var reference = asset.reference();
        var name = reference.getName();
        var description = reference.getDescription();

        // Icon based on source (could be enhanced later)
        var icon = new Span("\uD83C\uDFB5"); // 🎵
        icon.addClassName("inline-asset-icon");

        // Title
        var titleSpan = new Span(name);
        titleSpan.addClassName("inline-asset-title");

        // Subtitle (description)
        var subtitleSpan = new Span(description);
        subtitleSpan.addClassName("inline-asset-subtitle");

        // Text content layout
        var textLayout = new Div();
        textLayout.addClassName("inline-asset-text");
        textLayout.add(titleSpan, subtitleSpan);

        // Buttons layout
        var buttonsLayout = new FlexLayout();
        buttonsLayout.addClassName("inline-asset-buttons");
        buttonsLayout.getStyle().set("gap", "0.5em");

        // Find the primary play tool (first no-arg tool)
        var tools = reference.tools();
        if (tools != null && !tools.isEmpty() && toolInvoker != null) {
            for (var tool : tools) {
                if (isNoArgTool(tool)) {
                    var playButton = new Button(tool.getDefinition().getName());
                    playButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                    playButton.addClassName("inline-asset-play-btn");
                    playButton.addClickListener(e -> toolInvoker.accept(tool));
                    buttonsLayout.add(playButton);
                    break; // Only show first playable tool
                }
            }
        }

        // View in Assets button
        if (onViewInAssets != null) {
            var viewButton = new Button("Assets", VaadinIcon.ARROW_RIGHT.create());
            viewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            viewButton.addClassName("inline-asset-view-btn");
            viewButton.setIconAfterText(true);
            viewButton.addClickListener(e -> onViewInAssets.run());
            buttonsLayout.add(viewButton);
        }

        // Main layout
        var contentLayout = new FlexLayout();
        contentLayout.addClassName("inline-asset-content");
        contentLayout.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "0.75em")
                .set("flex-wrap", "wrap");
        contentLayout.add(icon, textLayout, buttonsLayout);

        add(contentLayout);
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
        return params.stream().noneMatch(Tool.Parameter::getRequired);
    }
}
