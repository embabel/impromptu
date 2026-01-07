package com.embabel.web.vaadin.components;

import com.embabel.agent.rag.model.NamedEntity;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Collapsible panel showing entities.
 * Generic component that works with any source of NamedEntity.
 */
public class EntitiesPanel extends Details {

    private final Supplier<List<? extends NamedEntity>> entitySupplier;
    private final VerticalLayout entitiesContent;
    private final Span entityCountSpan;
    private Consumer<NamedEntity> onEntityClick;

    /**
     * Create an entities panel.
     *
     * @param title          The panel title
     * @param entitySupplier Supplier that returns the list of entities to display
     */
    public EntitiesPanel(String title, Supplier<List<? extends NamedEntity>> entitySupplier) {
        this.entitySupplier = entitySupplier;

        // Header with count and refresh button
        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(VerticalLayout.Alignment.CENTER);
        headerLayout.setSpacing(true);

        var titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-m)");

        entityCountSpan = new Span("(0 entities)");
        entityCountSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh entities");
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, entityCountSpan, refreshButton);

        // Content area for entities
        entitiesContent = new VerticalLayout();
        entitiesContent.setPadding(false);
        entitiesContent.setSpacing(true);
        entitiesContent.setWidthFull();

        var contentScroller = new Scroller(entitiesContent);
        contentScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        contentScroller.setHeight("200px");
        contentScroller.setWidthFull();
        contentScroller.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-shade-5pct)");

        setSummary(headerLayout);
        setContent(contentScroller);
        addThemeVariants(DetailsVariant.FILLED);
        setWidthFull();
        getStyle().set("--vaadin-details-summary-padding", "var(--lumo-space-s) var(--lumo-space-m)");

        // Refresh when opened
        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                refresh();
            }
        });
    }

    /**
     * Set the handler for entity clicks.
     */
    public void setOnEntityClick(Consumer<NamedEntity> handler) {
        this.onEntityClick = handler;
    }

    /**
     * Refresh the entities list from the supplier.
     */
    public void refresh() {
        entitiesContent.removeAll();

        var entities = entitySupplier.get();
        entityCountSpan.setText("(" + entities.size() + " entities)");

        if (entities.isEmpty()) {
            var emptyMessage = new Span("No entities found.");
            emptyMessage.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic")
                    .set("padding", "var(--lumo-space-m)");
            entitiesContent.add(emptyMessage);
            return;
        }

        // Sort by name
        entities.stream()
                .sorted(Comparator.comparing(NamedEntity::getName))
                .forEach(entity -> {
                    var card = new EntityCard(entity);
                    if (onEntityClick != null) {
                        card.addEntityClickListener(e -> onEntityClick.accept(entity));
                    }
                    entitiesContent.add(card);
                });
    }

    /**
     * Schedule a refresh after a delay.
     */
    public void scheduleRefresh(com.vaadin.flow.component.UI ui, long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                ui.access(this::refresh);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
