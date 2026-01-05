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

    public ChatFooter(Neo4jConfig neo4jConfig, Runnable onAnalyze) {
        setWidthFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);
        setSpacing(true);
        getStyle().set("padding", "var(--lumo-space-s) 0");

        var logo = new Image(
                "https://docs.embabel.com/embabel-agent/guide/0.3.1/images/tower.png",
                "Embabel"
        );
        logo.setHeight("24px");
        logo.getStyle()
                .set("filter", "drop-shadow(0 0 4px rgba(201, 162, 39, 0.5))")
                .set("opacity", "0.85");

        var poweredBy = new Span("Powered by");
        poweredBy.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        var embabelLink = new Anchor("https://embabel.com", "Embabel");
        embabelLink.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("text-decoration", "none")
                .set("font-weight", "500");
        embabelLink.setTarget("_blank");

        // Separator
        var separator = new Span("|");
        separator.getStyle()
                .set("color", "var(--lumo-contrast-30pct)")
                .set("margin", "0 var(--lumo-space-s)");

        // Neo4j Browser button with auto-login
        var neo4jLink = new Anchor(neo4jConfig.browserUrl(), "Neo4j Browser");
        neo4jLink.getElement().setAttribute("router-ignore", true);
        neo4jLink.setTarget("_blank");
        neo4jLink.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("text-decoration", "none");

        // Separator
        var separator2 = new Span("|");
        separator2.getStyle()
                .set("color", "var(--lumo-contrast-30pct)")
                .set("margin", "0 var(--lumo-space-s)");

        // Analyze conversation button
        var analyzeButton = new Button("Analyze", e -> onAnalyze.run());
        analyzeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        analyzeButton.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-primary-text-color)");
        analyzeButton.getElement().setAttribute("title", "Extract propositions from conversation");

        add(logo, poweredBy, embabelLink, separator, neo4jLink, separator2, analyzeButton);
    }
}
