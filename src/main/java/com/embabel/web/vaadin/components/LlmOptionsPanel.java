package com.embabel.web.vaadin.components;

import com.embabel.common.ai.model.LlmOptions;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Reusable component to display LLM configuration options.
 * Shows model name and key hyperparameters in a compact format.
 */
public class LlmOptionsPanel extends VerticalLayout {

    public LlmOptionsPanel(String label, LlmOptions options) {
        setPadding(false);
        setSpacing(false);
        addClassName("llm-options-panel");

        // Label
        var labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        add(labelSpan);

        // Model name - prefer simple model string over verbose criteria
        var modelName = options.getModel() != null
                ? options.getModel()
                : options.getCriteria().toString();
        var modelSpan = new Span(modelName);
        modelSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-m)")
                .set("font-family", "var(--lumo-font-family-monospace, monospace)");
        add(modelSpan);

        // Hyperparameters row
        var paramsRow = new HorizontalLayout();
        paramsRow.setSpacing(true);
        paramsRow.setPadding(false);
        paramsRow.getStyle().set("flex-wrap", "wrap");

        if (options.getTemperature() != null) {
            paramsRow.add(createParamBadge("temp", formatNumber(options.getTemperature())));
        }
        if (options.getMaxTokens() != null) {
            paramsRow.add(createParamBadge("max", options.getMaxTokens().toString()));
        }
        if (options.getTopP() != null) {
            paramsRow.add(createParamBadge("topP", formatNumber(options.getTopP())));
        }
        if (options.getTopK() != null) {
            paramsRow.add(createParamBadge("topK", options.getTopK().toString()));
        }
        if (options.getFrequencyPenalty() != null) {
            paramsRow.add(createParamBadge("freq", formatNumber(options.getFrequencyPenalty())));
        }
        if (options.getPresencePenalty() != null) {
            paramsRow.add(createParamBadge("pres", formatNumber(options.getPresencePenalty())));
        }

        if (paramsRow.getComponentCount() > 0) {
            add(paramsRow);
        }
    }

    private Span createParamBadge(String name, String value) {
        var badge = new Span(name + ":" + value);
        badge.getElement().getThemeList().add("badge small contrast");
        badge.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace, monospace)");
        return badge;
    }

    private String formatNumber(Double value) {
        if (value == null) return "";
        if (value == value.intValue()) {
            return String.valueOf(value.intValue());
        }
        return String.format("%.2f", value);
    }
}
