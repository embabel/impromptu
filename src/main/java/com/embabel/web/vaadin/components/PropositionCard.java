package com.embabel.web.vaadin.components;

import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Card component displaying a single proposition with its metadata.
 */
public class PropositionCard extends Div {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public PropositionCard(Proposition prop) {
        getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("margin-bottom", "var(--lumo-space-xs)");

        // Proposition text
        var textSpan = new Span(prop.getText());
        textSpan.getStyle()
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        // Metadata line
        var metaLayout = new HorizontalLayout();
        metaLayout.setSpacing(true);
        metaLayout.getStyle().set("flex-wrap", "wrap");

        // Confidence badge
        var confidencePercent = (int) (prop.getConfidence() * 100);
        var confidenceSpan = new Span(confidencePercent + "% confidence");
        confidenceSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", confidencePercent >= 80 ? "var(--lumo-success-text-color)" :
                        confidencePercent >= 50 ? "var(--lumo-secondary-text-color)" :
                                "var(--lumo-error-text-color)")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "2px 8px")
                .set("border-radius", "var(--lumo-border-radius-s)");

        // Time
        var timeSpan = new Span(TIME_FORMATTER.format(prop.getCreated()));
        timeSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)");

        metaLayout.add(confidenceSpan, timeSpan);

        // Entity mentions as badges
        var mentions = prop.getMentions();
        if (!mentions.isEmpty()) {
            var entitiesLayout = new HorizontalLayout();
            entitiesLayout.setSpacing(false);
            entitiesLayout.getStyle()
                    .set("flex-wrap", "wrap")
                    .set("gap", "4px")
                    .set("margin-top", "var(--lumo-space-xs)");

            for (var mention : mentions) {
                var id = mention.getResolvedId() != null ?
                        mention.getResolvedId() :
                        "?";
                var badge = new Span(mention.getType() + ":" + id);
                badge.getStyle()
                        .set("font-size", "var(--lumo-font-size-xxs)")
                        .set("color", "var(--lumo-primary-text-color)")
                        .set("background", "var(--lumo-primary-color-10pct)")
                        .set("padding", "1px 6px")
                        .set("border-radius", "var(--lumo-border-radius-s)");
                entitiesLayout.add(badge);
            }
            add(textSpan, metaLayout, entitiesLayout);
        } else {
            add(textSpan, metaLayout);
        }
    }
}
