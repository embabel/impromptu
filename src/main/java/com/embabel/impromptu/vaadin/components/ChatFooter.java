package com.embabel.impromptu.vaadin.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Footer component with Embabel branding, Neo4j browser link, and analyze button.
 */
public class ChatFooter extends HorizontalLayout {

    /**
     * Configuration for Neo4j browser link.
     */
    public record Neo4jConfig(
            String host,
            int boltPort,
            String username,
            String password,
            int httpPort
    ) {
        public String browserUrl() {
            return String.format(
                    "http://%s:%d/browser/?connectURL=bolt://%s:%d&username=%s&password=%s",
                    host, httpPort, host, boltPort, username, password);
        }
    }

    public ChatFooter(Neo4jConfig neo4jConfig, Runnable onAnalyze, long chunkCount, long documentCount) {
        setWidthFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);
        setSpacing(true);
        addClassName("footer-container");

        var logo = new Image(
                "https://docs.embabel.com/embabel-agent/guide/0.3.1/images/tower.png",
                "Embabel"
        );
        logo.addClassName("footer-logo");

        var poweredBy = new Span("Powered by");
        poweredBy.addClassName("footer-powered-by");

        var embabelLink = new Anchor("https://embabel.com", "Embabel");
        embabelLink.addClassName("footer-embabel-link");
        embabelLink.setTarget("_blank");

        // Separator
        var separator = new Span("|");
        separator.addClassName("footer-separator");

        // Neo4j Browser button with auto-login
        var neo4jLink = new Anchor(neo4jConfig.browserUrl(), "Neo4j Browser");
        neo4jLink.getElement().setAttribute("router-ignore", true);
        neo4jLink.setTarget("_blank");
        neo4jLink.addClassName("footer-neo4j-link");

        // Separator
        var separator2 = new Span("|");
        separator2.addClassName("footer-separator");

        // Analyze conversation button
        var analyzeButton = new Button("Analyze", e -> onAnalyze.run());
        analyzeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        analyzeButton.addClassName("footer-analyze-button");
        analyzeButton.getElement().setAttribute("title", "Extract propositions from conversation");

        // Stats display
        var statsText = new Span(String.format("%,d chunks | %,d documents", chunkCount, documentCount));
        statsText.addClassName("footer-stats");

        add(logo, poweredBy, embabelLink, separator, neo4jLink, separator2, analyzeButton, statsText);
    }
}
