package com.embabel.impromptu.vaadin.components;

import com.vaadin.flow.component.html.Anchor;
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

        // Primary author
        var authorLabel = new Span("Primary author");
        authorLabel.getStyle()
                .set("font-size", "0.9rem")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.1em");

        var authorName = new Span("Rod Johnson");
        authorName.getStyle()
                .set("font-size", "1.5rem")
                .set("font-weight", "600")
                .set("color", "var(--lumo-primary-color)")
                .set("font-family", "var(--lumo-font-family)");

        var authorSection = new VerticalLayout(authorLabel, authorName);
        authorSection.setPadding(false);
        authorSection.setSpacing(false);
        authorSection.getStyle().set("gap", "var(--lumo-space-xs)");
        add(authorSection);

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
        logos.getStyle().set("gap", "var(--lumo-space-l)").set("flex-wrap", "wrap");

        // Embabel logo with link
        var embabelLogo = new Image("https://raw.githubusercontent.com/embabel/embabel-agent/refs/heads/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg", "Embabel");
        embabelLogo.setHeight("64px");
        var embabelLink = new Anchor("https://embabel.com", embabelLogo);
        embabelLink.setTarget("_blank");

        // Spring logo
        var springLogo = new Image("https://spring.io/img/spring.svg", "Spring Boot");
        springLogo.setHeight("64px");
        var springLink = new Anchor("https://spring.io", springLogo);
        springLink.setTarget("_blank");

        // Neo4j logo with link
        var neo4jLogo = new Image("https://dist.neo4j.com/wp-content/uploads/20210423062553/neo4j-social-share-21.png", "Neo4j");
        neo4jLogo.setHeight("64px");
        var neo4jLink = new Anchor("https://neo4j.com", neo4jLogo);
        neo4jLink.setTarget("_blank");

        // Vaadin logo with link
        var vaadinLogo = new Image("https://brand.vaadin.com/images/vaadin-logo-dark.svg", "Vaadin");
        vaadinLogo.setHeight("64px");
        var vaadinLogoLink = new Anchor("https://vaadin.com", vaadinLogo);
        vaadinLogoLink.setTarget("_blank");

        // Apache logo with link
        var apacheLogo = new Image("https://www.apache.org/foundation/press/kit/asf_logo_url.png", "Apache Software Foundation");
        apacheLogo.setHeight("64px");
        var apacheLink = new Anchor("https://www.apache.org", apacheLogo);
        apacheLink.setTarget("_blank");

        logos.add(embabelLink, springLink, neo4jLink, vaadinLogoLink, apacheLink);
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

        // Built with Claude Code (with logo)
        var claudeInfo = new HorizontalLayout();
        claudeInfo.setAlignItems(Alignment.CENTER);
        claudeInfo.setSpacing(true);
        var claudeLabel = new Span("Built using ");
        var claudeLogo = new Image("https://cdn.worldvectorlogo.com/logos/anthropic-1.svg", "Claude");
        claudeLogo.setHeight("24px");
        claudeLogo.getStyle().set("vertical-align", "middle");
        var claudeLink = new Anchor("https://claude.ai/claude-code", "Claude Code");
        claudeLink.setTarget("_blank");
        claudeInfo.add(claudeLabel, claudeLogo, claudeLink);
        add(claudeInfo);

        // Repository
        var repoRow = new HorizontalLayout();
        repoRow.setAlignItems(Alignment.CENTER);
        repoRow.setSpacing(true);
        repoRow.getStyle().set("margin-top", "var(--lumo-space-l)");
        var repoLabel = new Span("Source: ");
        var repoLink = new Anchor("https://github.com/embabel/impromptu", "github.com/embabel/impromptu");
        repoLink.setTarget("_blank");
        repoRow.add(repoLabel, repoLink);
        add(repoRow);

        // License (under source, above copyright)
        var license = new Span("Licensed under the Apache License 2.0");
        license.getStyle()
                .set("font-size", "1rem")
                .set("color", "var(--lumo-secondary-text-color)");
        add(license);

        // Copyright at bottom
        var copyright = new Span("\u00A9 Embabel 2026");
        copyright.getStyle()
                .set("font-size", "1rem")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-l)");
        add(copyright);
    }
}
