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

import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Card component displaying a single proposition with its metadata.
 * Entity mentions can be made clickable by providing a mention click handler.
 */
public class PropositionCard extends Div {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Proposition proposition;
    private Consumer<EntityMention> onMentionClick;
    private Consumer<Proposition> onDelete;
    private HorizontalLayout entitiesLayout;

    public PropositionCard(Proposition prop) {
        this.proposition = prop;
        addClassName("proposition-card");

        // Header with text and delete button
        var headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setSpacing(true);
        headerLayout.addClassName("proposition-header");

        // Proposition text
        var textSpan = new Span(prop.getText());
        textSpan.addClassName("proposition-text");

        // Delete button
        var deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.addClassName("proposition-delete");
        deleteButton.getElement().setAttribute("title", "Delete this memory");
        deleteButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(proposition);
            }
        });

        headerLayout.add(textSpan, deleteButton);
        headerLayout.setFlexGrow(1, textSpan);

        // Metadata line
        var metaLayout = new HorizontalLayout();
        metaLayout.setSpacing(true);
        metaLayout.addClassName("proposition-meta");

        // Confidence badge
        var confidencePercent = (int) (prop.getConfidence() * 100);
        var confidenceSpan = new Span(confidencePercent + "% confidence");
        confidenceSpan.addClassName("proposition-confidence");
        confidenceSpan.addClassName(confidencePercent >= 80 ? "high" :
                confidencePercent >= 50 ? "medium" : "low");

        // Time
        var timeSpan = new Span(TIME_FORMATTER.format(prop.getCreated()));
        timeSpan.addClassName("proposition-time");

        metaLayout.add(confidenceSpan, timeSpan);

        // Entity mentions as badges
        var mentions = prop.getMentions();
        if (!mentions.isEmpty()) {
            entitiesLayout = new HorizontalLayout();
            entitiesLayout.setSpacing(false);
            entitiesLayout.addClassName("proposition-entities");

            for (var mention : mentions) {
                entitiesLayout.add(createMentionBadge(mention));
            }
            add(headerLayout, metaLayout, entitiesLayout);
        } else {
            add(headerLayout, metaLayout);
        }
    }

    /**
     * Create a badge for an entity mention.
     */
    private Span createMentionBadge(EntityMention mention) {
        var id = mention.getResolvedId() != null ? mention.getResolvedId() : "?";
        var badge = new Span(mention.getType() + ":" + id);
        badge.addClassName("mention-badge");

        // Make clickable if resolved and handler is set
        if (mention.getResolvedId() != null) {
            badge.addClassName("clickable");
            badge.getElement().addEventListener("click", e -> {
                if (onMentionClick != null) {
                    onMentionClick.accept(mention);
                }
            });
        }

        return badge;
    }

    /**
     * Set the handler for mention clicks.
     * Only resolved mentions (with an ID) are clickable.
     */
    public void setOnMentionClick(Consumer<EntityMention> handler) {
        this.onMentionClick = handler;
    }

    /**
     * Set the handler for deleting this proposition.
     */
    public void setOnDelete(Consumer<Proposition> handler) {
        this.onDelete = handler;
    }

    /**
     * Get the proposition this card displays.
     */
    public Proposition getProposition() {
        return proposition;
    }
}
