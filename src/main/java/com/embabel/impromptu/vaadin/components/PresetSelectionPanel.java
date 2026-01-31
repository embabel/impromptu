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
import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Panel for selecting a preset (bundled theme + personality settings).
 * Selecting a preset applies theme, persona, and maxWords together.
 */
public class PresetSelectionPanel extends VerticalLayout {

    /**
     * Wrapper to display preset name and info in the radio group.
     */
    public record PresetChoice(String name, ImpromptuProperties.Preset preset) {
        public String displayName() {
            // Capitalize first letter
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    public PresetSelectionPanel(
            ImpromptuProperties properties,
            ImpromptuUser user,
            Consumer<ImpromptuProperties.Preset> onPresetSelected) {

        setPadding(true);
        setSpacing(true);

        var header = new Span("Quick Start");
        header.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "600")
                .set("margin-bottom", "var(--lumo-space-s)");
        add(header);

        var description = new Span("Choose a preset to set theme and personality together");
        description.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "var(--lumo-space-m)");
        add(description);

        Map<String, ImpromptuProperties.Preset> presets = properties.presets();
        if (presets == null || presets.isEmpty()) {
            var noPresets = new Span("No presets configured");
            noPresets.getStyle().set("color", "var(--lumo-secondary-text-color)");
            add(noPresets);
            return;
        }

        // Convert to list for radio group
        List<PresetChoice> choices = presets.entrySet().stream()
                .map(e -> new PresetChoice(e.getKey(), e.getValue()))
                .toList();

        var radioGroup = new RadioButtonGroup<PresetChoice>();
        radioGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        radioGroup.setItems(choices);
        radioGroup.setRenderer(new ComponentRenderer<>(choice -> {
            var item = new VerticalLayout();
            item.setPadding(false);
            item.setSpacing(false);
            item.getStyle().set("gap", "var(--lumo-space-xs)");

            var name = new Span(choice.displayName());
            name.getStyle()
                    .set("font-weight", "500")
                    .set("font-size", "var(--lumo-font-size-m)");

            var preset = choice.preset();
            var desc = new Span(preset.description() != null ? preset.description() : "");
            desc.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");

            item.add(name, desc);
            return item;
        }));

        // Try to find current preset based on user's current theme
        String currentTheme = user.getTheme();
        choices.stream()
                .filter(c -> c.preset().theme().equals(currentTheme))
                .findFirst()
                .ifPresent(radioGroup::setValue);

        radioGroup.addValueChangeListener(event -> {
            if (event.getValue() != null && onPresetSelected != null) {
                onPresetSelected.accept(event.getValue().preset());
            }
        });

        add(radioGroup);
    }
}
