package com.embabel.impromptu;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
        String behaviour,
        @NestedConfigurationProperty Voice voice,
        @NestedConfigurationProperty Extraction extraction,
        @DefaultValue @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @NestedConfigurationProperty LlmOptions propositionExtractionLlm,
        @NestedConfigurationProperty LlmOptions entityResolutionLlm,
        boolean showExtractionPrompts,
        boolean showExtractionResponses
) {

    public record Voice(
            String persona,
            int maxWords
    ) {
    }

    /**
     * Configuration for proposition extraction from conversations.
     *
     * @param windowSize      number of messages to include in each extraction window
     * @param overlapSize     number of messages to overlap for context continuity
     * @param triggerInterval extract propositions every N messages (0 = manual only)
     */
    public record Extraction(
            int windowSize,
            int overlapSize,
            int triggerInterval
    ) {
        public Extraction {
            if (windowSize <= 0) windowSize = 10;
            if (overlapSize < 0) overlapSize = 2;
            if (triggerInterval < 0) triggerInterval = 10;
        }
    }
}
