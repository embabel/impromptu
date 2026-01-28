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

import com.embabel.agent.rag.model.RelationshipDirection;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.agent.rag.service.RetrievableIdentifier;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * References panel showing domain entity statistics and composer list.
 */
public class ReferencesPanel extends VerticalLayout {

    public ReferencesPanel(NamedEntityDataRepository entityRepository) {
        setPadding(true);
        setSpacing(true);
        setVisible(false);
        setSizeFull();

        // Entity counts
        var works = entityRepository.findByLabel("Work");
        var techniques = entityRepository.findByLabel("Technique");
        var nationalities = entityRepository.findByLabel("Nationality");
        var epochs = entityRepository.findByLabel("Epoch");
        var instruments = entityRepository.findByLabel("Instrument");
        var composers = entityRepository.findByLabel("Composer");

        // Entity statistics grid
        var statsGrid = new HorizontalLayout();
        statsGrid.setWidthFull();
        statsGrid.setJustifyContentMode(JustifyContentMode.START);
        statsGrid.setSpacing(true);
        statsGrid.getStyle().set("flex-wrap", "wrap");

        statsGrid.add(createStatBadge("Works", works.size()));
        statsGrid.add(createStatBadge("Techniques", techniques.size()));
        statsGrid.add(createStatBadge("Nationalities", nationalities.size()));
        statsGrid.add(createStatBadge("Epochs", epochs.size()));
        statsGrid.add(createStatBadge("Instruments", instruments.size()));

        add(statsGrid);
        add(new Hr());

        // Composers header
        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.START);

        var composersCount = new Span(composers.size() + " Composers");
        composersCount.getStyle().set("font-weight", "bold");

        header.add(composersCount);
        add(header);

        // Composer list
        var composerList = new VerticalLayout();
        composerList.setPadding(false);
        composerList.setSpacing(false);

        composers.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .forEach(composer -> {
                    var item = new HorizontalLayout();
                    item.setWidthFull();
                    item.setAlignItems(Alignment.CENTER);
                    item.getStyle()
                            .set("padding", "var(--lumo-space-xs) 0")
                            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

                    var name = new Span(composer.getName());
                    name.getStyle().set("flex-grow", "1");

                    var workCount = entityRepository.findRelated(
                            new RetrievableIdentifier(composer.getId(), "Composer"),
                            "COMPOSED",
                            RelationshipDirection.OUTGOING
                    ).size();

                    var count = new Span(workCount + " works");
                    count.getStyle()
                            .set("color", "var(--lumo-secondary-text-color)")
                            .set("font-size", "var(--lumo-font-size-s)");

                    item.add(name, count);
                    composerList.add(item);
                });

        var scroller = new Scroller(composerList);
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.setSizeFull();

        add(scroller);
        setFlexGrow(1, scroller);
    }

    private Span createStatBadge(String label, int count) {
        var badge = new Span(String.format("%,d %s", count, label));
        badge.getElement().getThemeList().add("badge");
        badge.getStyle()
                .set("font-size", "var(--lumo-font-size-s)");
        return badge;
    }
}
