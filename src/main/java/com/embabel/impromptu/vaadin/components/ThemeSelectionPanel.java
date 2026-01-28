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

import com.embabel.impromptu.theme.ThemeService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.function.Consumer;

/**
 * Panel for selecting the UI theme.
 */
public class ThemeSelectionPanel extends VerticalLayout {

    public ThemeSelectionPanel(ThemeService themeService, ImpromptuUser user, Consumer<String> onThemeChange) {
        setPadding(true);
        setSpacing(true);

        var header = new Span("Theme");
        header.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "600")
                .set("margin-bottom", "var(--lumo-space-s)");
        add(header);

        var description = new Span("Choose the visual style for the application");
        description.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "var(--lumo-space-m)");
        add(description);

        var themes = themeService.getAvailableThemes();

        if (themes.isEmpty()) {
            var noThemes = new Span("No themes available");
            noThemes.getStyle().set("color", "var(--lumo-secondary-text-color)");
            add(noThemes);
            return;
        }

        var radioGroup = new RadioButtonGroup<ThemeService.ThemeInfo>();
        radioGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        radioGroup.setItems(themes);
        radioGroup.setRenderer(new ComponentRenderer<>(theme -> {
            var item = new VerticalLayout();
            item.setPadding(false);
            item.setSpacing(false);
            item.getStyle().set("gap", "var(--lumo-space-xs)");

            var name = new Span(theme.displayName());
            name.getStyle()
                    .set("font-weight", "500")
                    .set("font-size", "var(--lumo-font-size-m)");

            var desc = new Span(theme.description());
            desc.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");

            item.add(name, desc);
            return item;
        }));

        // Set current selection
        var currentTheme = user.getTheme();
        if (currentTheme != null) {
            themes.stream()
                    .filter(t -> t.name().equals(currentTheme))
                    .findFirst()
                    .ifPresent(radioGroup::setValue);
        } else {
            // Select default
            themes.stream()
                    .filter(ThemeService.ThemeInfo::isDefault)
                    .findFirst()
                    .ifPresent(radioGroup::setValue);
        }

        radioGroup.addValueChangeListener(event -> {
            if (event.getValue() != null && onThemeChange != null) {
                onThemeChange.accept(event.getValue().name());
            }
        });

        add(radioGroup);
    }
}
