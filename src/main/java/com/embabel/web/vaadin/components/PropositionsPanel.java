package com.embabel.web.vaadin.components;

import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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
    private Runnable onClear;

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

        var titleSpan = new Span("Knowledge Base");
        titleSpan.addClassName("panel-title");

        propositionCountSpan = new Span("(0 propositions)");
        propositionCountSpan.addClassName("panel-count");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh propositions");
        refreshButton.addClickListener(e -> refresh());

        var clearButton = new Button(VaadinIcon.TRASH.create());
        clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        clearButton.getElement().setAttribute("title", "Clear all propositions");
        clearButton.addClickListener(e -> {
            if (onClear != null) {
                var dialog = new ConfirmDialog();
                dialog.setHeader("Clear Knowledge Base");
                dialog.setText("This will permanently delete ALL extracted propositions. This action cannot be undone.");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete All");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(event -> {
                    onClear.run();
                    refresh();
                });
                dialog.open();
            }
        });

        headerLayout.add(titleSpan, propositionCountSpan, refreshButton, clearButton);
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
     * Set the handler for clearing all propositions.
     */
    public void setOnClear(Runnable handler) {
        this.onClear = handler;
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
