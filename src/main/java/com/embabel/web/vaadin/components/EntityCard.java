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

        getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("margin-bottom", "var(--lumo-space-xs)")
                .set("cursor", "pointer");

        // Header with type badge and name
        var headerLayout = new HorizontalLayout();
        headerLayout.setSpacing(true);
        headerLayout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerLayout.getStyle().set("margin-bottom", "var(--lumo-space-xs)");

        // Type badge (most specific label, excluding entity label)
        var typeLabel = getPrimaryLabel(entity.labels());
        var typeBadge = new Span(typeLabel);
        typeBadge.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("padding", "2px 8px")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-weight", "500");

        // Entity name
        var nameSpan = new Span(entity.getName());
        nameSpan.getStyle()
                .set("font-weight", "500");

        headerLayout.add(typeBadge, nameSpan);

        // Description
        var descSpan = new Span(entity.getDescription());
        descSpan.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        // ID (smaller, tertiary)
        var idSpan = new Span("id: " + entity.getId());
        idSpan.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("margin-top", "var(--lumo-space-xs)");

        add(headerLayout, descSpan, idSpan);

        // Hover effect
        getElement().addEventListener("mouseenter", e ->
                getStyle().set("background", "var(--lumo-contrast-10pct)"));
        getElement().addEventListener("mouseleave", e ->
                getStyle().set("background", "var(--lumo-contrast-5pct)"));
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
