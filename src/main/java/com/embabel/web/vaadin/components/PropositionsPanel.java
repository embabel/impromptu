package com.embabel.web.vaadin.components;

import com.embabel.dice.proposition.EntityMention;
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
import java.util.function.Consumer;

/**
 * Collapsible panel showing the knowledge base of extracted propositions.
 */
public class PropositionsPanel extends Details {

    private final PropositionRepository propositionRepository;
    private final VerticalLayout propositionsContent;
    private final Span propositionCountSpan;
    private Consumer<EntityMention> onMentionClick;

    public PropositionsPanel(PropositionRepository propositionRepository) {
        this.propositionRepository = propositionRepository;

        // Header with count and refresh button
        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(VerticalLayout.Alignment.CENTER);
        headerLayout.setSpacing(true);

        var titleSpan = new Span("Knowledge Base");
        titleSpan.addClassName("panel-title");

        propositionCountSpan = new Span("(0 propositions)");
        propositionCountSpan.addClassName("panel-count");

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
        contentScroller.addClassName("panel-scroller");

        setSummary(headerLayout);
        setContent(contentScroller);
        addThemeVariants(DetailsVariant.FILLED);
        setWidthFull();

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
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        // Sort by created time (newest first)
        propositions.stream()
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .forEach(prop -> {
                    var card = new PropositionCard(prop);
                    if (onMentionClick != null) {
                        card.setOnMentionClick(onMentionClick);
                    }
                    propositionsContent.add(card);
                });
    }

    /**
     * Set the handler for mention clicks in proposition cards.
     */
    public void setOnMentionClick(Consumer<EntityMention> handler) {
        this.onMentionClick = handler;
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
