package com.embabel.web.vaadin.components;

import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
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
    private HorizontalLayout entitiesLayout;

    public PropositionCard(Proposition prop) {
        this.proposition = prop;
        addClassName("proposition-card");

        // Proposition text
        var textSpan = new Span(prop.getText());
        textSpan.addClassName("proposition-text");

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
            add(textSpan, metaLayout, entitiesLayout);
        } else {
            add(textSpan, metaLayout);
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
     * Get the proposition this card displays.
     */
    public Proposition getProposition() {
        return proposition;
    }
}
