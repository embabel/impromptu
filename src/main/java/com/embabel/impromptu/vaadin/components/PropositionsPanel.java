package com.embabel.impromptu.vaadin.components;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
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

/**
 * Collapsible panel showing the knowledge base of extracted propositions.
 */
public class PropositionsPanel extends Details {

    private final PropositionRepository propositionRepository;
    private final VerticalLayout propositionsContent;
    private final Span propositionCountSpan;

    public PropositionsPanel(PropositionRepository propositionRepository) {
        this.propositionRepository = propositionRepository;

        // Header with count and refresh button
        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(VerticalLayout.Alignment.CENTER);
        headerLayout.setSpacing(true);

        var titleSpan = new Span("Knowledge Base");
        titleSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-m)");

        propositionCountSpan = new Span("(0 propositions)");
        propositionCountSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh propositions");
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, propositionCountSpan, refreshButton);

        // Content area for propositions
        propositionsContent = new VerticalLayout();
        propositionsContent.setPadding(false);
        propositionsContent.setSpacing(true);
        propositionsContent.setWidthFull();

        var contentScroller = new Scroller(propositionsContent);
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
     * Refresh the propositions list from the repository.
     */
    public void refresh() {
        propositionsContent.removeAll();

        var propositions = propositionRepository.findAll();
        propositionCountSpan.setText("(" + propositions.size() + " propositions)");

        if (propositions.isEmpty()) {
            var emptyMessage = new Span("No propositions extracted yet. Start a conversation to build the knowledge base.");
            emptyMessage.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic")
                    .set("padding", "var(--lumo-space-m)");
            propositionsContent.add(emptyMessage);
            return;
        }

        // Sort by created time (newest first)
        propositions.stream()
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .forEach(prop -> propositionsContent.add(new PropositionCard(prop)));
    }

    /**
     * Schedule a refresh after a delay (for async proposition extraction).
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
