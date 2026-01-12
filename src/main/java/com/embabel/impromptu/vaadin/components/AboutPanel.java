package com.embabel.impromptu.vaadin.components;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * About panel showing application information, credits, and links.
 */
public class AboutPanel extends VerticalLayout {

    public AboutPanel() {
        setPadding(true);
        setSpacing(true);
        setVisible(false);
        getStyle().set("font-size", "1.25rem");

        // Definition
        var definition = new Div();
        definition.getStyle()
                .set("font-style", "italic")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-bottom", "var(--lumo-space-l)")
                .set("padding", "var(--lumo-space-l)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "1.25rem");
        definition.setText("impromptu (noun): a short piece of instrumental music, especially a solo, that is reminiscent of an improvisation.");
        add(definition);

        // Copyright
        var copyright = new Span("\u00A9 Embabel 2026");
        copyright.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "1.75rem");
        add(copyright);

        // Primary author
        var author = new Span("Primary author: Rod Johnson");
        author.getStyle()
                .set("margin-top", "var(--lumo-space-xs)")
                .set("font-size", "1.25rem");
        add(author);

        // Powered by section with logos
        var poweredBy = new Span("Powered by");
        poweredBy.getStyle()
                .set("font-size", "1.1rem")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-l)");
        add(poweredBy);

        var logos = new HorizontalLayout();
        logos.setAlignItems(Alignment.CENTER);
        logos.setSpacing(true);
        logos.getStyle().set("gap", "var(--lumo-space-l)");

        var embabelLogo = new Image("https://raw.githubusercontent.com/embabel/embabel-agent/refs/heads/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg", "Embabel");
        embabelLogo.setHeight("64px");
        var springLogo = new Image("https://spring.io/img/spring.svg", "Spring Boot");
        springLogo.setHeight("64px");
        var neo4jLogo = new Image("https://repository-images.githubusercontent.com/6650539/43471900-d341-11eb-8245-9fd59861ccc8", "Neo4j");
        neo4jLogo.setHeight("64px");

        logos.add(embabelLogo, springLogo, neo4jLogo);
        add(logos);

        // Data source
        var dataSource = new HorizontalLayout();
        dataSource.setAlignItems(Alignment.CENTER);
        dataSource.setSpacing(true);
        dataSource.getStyle().set("margin-top", "var(--lumo-space-l)");
        var dataLabel = new Span("Music data from ");
        var openOpusLink = new Anchor("https://openopus.org/", "Open Opus");
        openOpusLink.setTarget("_blank");
        dataSource.add(dataLabel, openOpusLink);
        add(dataSource);

        // UI
        var uiInfo = new HorizontalLayout();
        uiInfo.setAlignItems(Alignment.CENTER);
        uiInfo.setSpacing(true);
        var uiLabel = new Span("UI built with ");
        var vaadinLink = new Anchor("https://vaadin.com/", "Vaadin");
        vaadinLink.setTarget("_blank");
        uiInfo.add(uiLabel, vaadinLink);
        add(uiInfo);

        // Built with Claude Code
        var claudeInfo = new Span("Built using Claude Code");
        claudeInfo.getStyle().set("margin-top", "var(--lumo-space-s)");
        add(claudeInfo);

        // License
        var license = new Span("Licensed under the Apache License 2.0");
        license.getStyle()
                .set("font-size", "1rem")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-l)");
        add(license);

        // Repository
        var repoRow = new HorizontalLayout();
        repoRow.setAlignItems(Alignment.CENTER);
        repoRow.setSpacing(true);
        var repoLabel = new Span("Source: ");
        var repoLink = new Anchor("https://github.com/embabel/impromptu", "github.com/embabel/impromptu");
        repoLink.setTarget("_blank");
        repoRow.add(repoLabel, repoLink);
        add(repoRow);
    }
}
