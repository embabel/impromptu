package com.embabel.web.vaadin.components;

import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Panel showing the knowledge base of extracted propositions.
 */
public class PropositionsPanel extends VerticalLayout {

    private final PropositionRepository propositionRepository;
    private final VerticalLayout propositionsContent;
    private final Span propositionCountSpan;
    private Consumer<EntityMention> onMentionClick;
    private Consumer<String> onDelete;
    private String contextId;

    public PropositionsPanel(PropositionRepository propositionRepository) {
        this.propositionRepository = propositionRepository;

        setPadding(false);
        setSpacing(true);
        setSizeFull();

        // Header with count and refresh button
        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setSpacing(true);
        headerLayout.setWidthFull();

        var titleSpan = new Span("Memories");
        titleSpan.addClassName("panel-title");

        propositionCountSpan = new Span("(0 memories)");
        propositionCountSpan.addClassName("panel-count");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh memories");
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, propositionCountSpan, refreshButton);
        headerLayout.setFlexGrow(1, titleSpan);

        // Content area for propositions
        propositionsContent = new VerticalLayout();
        propositionsContent.setPadding(false);
        propositionsContent.setSpacing(true);
        propositionsContent.setWidthFull();

        var contentScroller = new Scroller(propositionsContent);
        contentScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        contentScroller.setSizeFull();
        contentScroller.addClassName("panel-scroller");

        add(headerLayout, contentScroller);
        setFlexGrow(1, contentScroller);
    }

    /**
     * Refresh the propositions list from the repository.
     * If a contextId is set, only propositions for that context are shown.
     */
    public void refresh() {
        propositionsContent.removeAll();

        var propositions = contextId != null
                ? propositionRepository.findByContextIdValue(contextId)
                : propositionRepository.findAll();
        propositionCountSpan.setText("(" + propositions.size() + " memories)");

        if (propositions.isEmpty()) {
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
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
                    if (onDelete != null) {
                        card.setOnDelete(p -> {
                            onDelete.accept(p.getId());
                            refresh();
                        });
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
     * Set the handler for deleting a single proposition by ID.
     */
    public void setOnDelete(Consumer<String> handler) {
        this.onDelete = handler;
    }

    /**
     * Set the context ID to filter propositions.
     * When set, only propositions for this context are shown and cleared.
     *
     * @param contextId the context ID, or null to show all propositions
     */
    public void setContextId(String contextId) {
        this.contextId = contextId;
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
