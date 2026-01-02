package com.embabel.impromptu;

import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Properties for chatbot
 *
 * @param chatLlm   LLM model and hyperparameters to use
 * @param objective the goal of the chatbot's responses: For example, to answer legal questions
 * @param voice     the persona and output style of the chatbot while achieving its objective
 * @param neoRag    configuration for ingestion
 */
@ConfigurationProperties(prefix = "impromptu")
public record ImpromptuProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        String embeddingService,
        String objective,
        @NestedConfigurationProperty Voice voice,
        @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty LlmOptions propositionExtractionLlm,
        boolean showExtractionPrompts,
        boolean showExtractionResponses
) {

    public record Voice(
            String persona,
            int maxWords
    ) {
    }
}
