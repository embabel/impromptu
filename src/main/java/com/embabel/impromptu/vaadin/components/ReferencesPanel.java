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
 * References panel showing indexed content statistics and domain entities.
 */
public class ReferencesPanel extends VerticalLayout {

    /**
     * Statistics about indexed content.
     */
    public record IndexStats(long chunkCount, long documentCount) {}

    public ReferencesPanel(NamedEntityDataRepository entityRepository, IndexStats stats) {
        setPadding(true);
        setSpacing(true);
        setVisible(false);
        setSizeFull();

        // Index statistics header
        var statsRow = new HorizontalLayout();
        statsRow.setWidthFull();
        statsRow.setJustifyContentMode(JustifyContentMode.START);
        statsRow.setSpacing(true);

        var chunksLabel = new Span(String.format("%,d chunks", stats.chunkCount()));
        chunksLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");

        var separator = new Span("|");
        separator.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-l)");

        var docsLabel = new Span(String.format("%,d documents", stats.documentCount()));
        docsLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");

        statsRow.add(chunksLabel, separator, docsLabel);
        add(statsRow);

        // Divider
        add(new Hr());

        var composers = entityRepository.findByLabel("Composer");
        var works = entityRepository.findByLabel("Work");

        // Composers/Works header
        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        var composersCount = new Span(composers.size() + " Composers");
        composersCount.getStyle().set("font-weight", "bold");

        var worksCount = new Span(works.size() + " Works");
        worksCount.getStyle().set("color", "var(--lumo-secondary-text-color)");

        header.add(composersCount, worksCount);
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
}
