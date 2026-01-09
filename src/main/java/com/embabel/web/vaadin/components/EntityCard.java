package com.embabel.web.vaadin.components;

import com.embabel.agent.rag.model.EntityData;
import com.embabel.agent.rag.model.NamedEntity;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Set;

/**
 * Card component displaying a single entity with its type and description.
 * Generic component that works with any NamedEntity.
 */
public class EntityCard extends Div {

    private final NamedEntity entity;

    public EntityCard(NamedEntity entity) {
        this.entity = entity;
        addClassName("entity-card");

        // Header with type badge and name
        var headerLayout = new HorizontalLayout();
        headerLayout.setSpacing(true);
        headerLayout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerLayout.addClassName("entity-header");

        // Type badge (most specific label, excluding entity label)
        var typeLabel = getPrimaryLabel(entity.labels());
        var typeBadge = new Span(typeLabel);
        typeBadge.addClassName("entity-type-badge");

        // Entity name
        var nameSpan = new Span(entity.getName());
        nameSpan.addClassName("entity-name");

        headerLayout.add(typeBadge, nameSpan);

        // Description
        var descSpan = new Span(entity.getDescription());
        descSpan.addClassName("entity-description");

        // ID (smaller, tertiary)
        var idSpan = new Span("id: " + entity.getId());
        idSpan.addClassName("entity-id");

        add(headerLayout, descSpan, idSpan);
    }

    /**
     * Get the primary (most specific) label for display.
     * Excludes generic labels like "Entity" and "Reference".
     */
    private String getPrimaryLabel(Set<String> labels) {
        return labels.stream()
                .filter(l -> !l.equals(EntityData.ENTITY_LABEL) && !l.equals("Reference"))
                .findFirst()
                .orElse(labels.stream().findFirst().orElse(EntityData.ENTITY_LABEL));
    }

    /**
     * Get the entity this card displays.
     */
    public NamedEntity getEntity() {
        return entity;
    }

    /**
     * Add a click listener to this card.
     */
    public void addEntityClickListener(ComponentEventListener<ClickEvent<Div>> listener) {
        addClickListener(listener);
    }
}
