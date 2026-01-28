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

import com.embabel.impromptu.speech.PersonaService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.function.Consumer;

/**
 * Panel for selecting the assistant's voice/persona.
 */
public class VoiceSelectionPanel extends VerticalLayout {

    public VoiceSelectionPanel(PersonaService personaService, ImpromptuUser user, Consumer<String> onPersonaChange) {
        setPadding(true);
        setSpacing(true);

        var header = new Span("Assistant Voice");
        header.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "600")
                .set("margin-bottom", "var(--lumo-space-s)");
        add(header);

        var description = new Span("Choose how the assistant communicates with you");
        description.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "var(--lumo-space-m)");
        add(description);

        var personas = personaService.getAvailablePersonas();

        if (personas.isEmpty()) {
            var noPersonas = new Span("No voice options available");
            noPersonas.getStyle().set("color", "var(--lumo-secondary-text-color)");
            add(noPersonas);
            return;
        }

        var radioGroup = new RadioButtonGroup<PersonaService.PersonaInfo>();
        radioGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        radioGroup.setItems(personas);
        radioGroup.setRenderer(new ComponentRenderer<>(persona -> {
            var item = new VerticalLayout();
            item.setPadding(false);
            item.setSpacing(false);
            item.getStyle().set("gap", "var(--lumo-space-xs)");

            var name = new Span(persona.displayName());
            name.getStyle()
                    .set("font-weight", "500")
                    .set("font-size", "var(--lumo-font-size-m)");

            var desc = new Span(persona.description());
            desc.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");

            item.add(name, desc);
            return item;
        }));

        // Set current selection
        var currentVoice = user.getVoice();
        if (currentVoice != null) {
            personas.stream()
                    .filter(p -> p.name().equals(currentVoice))
                    .findFirst()
                    .ifPresent(radioGroup::setValue);
        } else {
            // Select default
            personas.stream()
                    .filter(PersonaService.PersonaInfo::isDefault)
                    .findFirst()
                    .ifPresent(radioGroup::setValue);
        }

        radioGroup.addValueChangeListener(event -> {
            if (event.getValue() != null && onPersonaChange != null) {
                onPersonaChange.accept(event.getValue().name());
            }
        });

        add(radioGroup);
    }
}
