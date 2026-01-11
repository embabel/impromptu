package com.embabel.impromptu;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;

/**
 * Properties for chatbot. See application.yml
 *
 * @param chatLlm                  LLM model and hyperparameters to use
 *                                 for chatbot responses
 * @param embeddingService         the name of the embedding service to use
 *                                 for retrieval-augmented generation
 * @param objective                the goal of the chatbot's responses: For example, to answer legal questions
 * @param defaultVoice             the persona and output style of the chatbot while achieving its objective
 * @param extraction               configuration for extraction of propositions from conversations
 * @param neoRag                   Neo RAG configuration
 * @param chunkerConfig            content chunker configuration
 *                                 for RAG ingestion
 * @param propositionExtractionLlm LLM model and hyperparameters to use
 *                                 for proposition extraction from conversations
 * @param entityResolutionLlm      LLM model and hyperparameters to use
 *                                 for entity resolution during proposition extraction
 * @param showExtractionPrompts    whether to log the extraction prompts
 * @param showExtractionResponses  whether to log the extraction responses
 */
@ConfigurationProperties(prefix = "impromptu")
public record ImpromptuProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        String embeddingService,
        String objective,
        String behaviour,
        @DefaultValue("50") int conversationWindow,
        @NestedConfigurationProperty Voice defaultVoice,
        @NestedConfigurationProperty Extraction extraction,
        @DefaultValue @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @NestedConfigurationProperty LlmOptions propositionExtractionLlm,
        @NestedConfigurationProperty LlmOptions entityResolutionLlm,
        boolean showExtractionPrompts,
        boolean showExtractionResponses,
        @Nullable @NestedConfigurationProperty Speech speech
) {

    public record Voice(
            String persona,
            int maxWords
    ) {
    }

    public record Speech(
            String ttsModel,
            String ttsVoice,
            String apiKey
    ) {
    }

    /**
     * Check if STT is configured (API key available).
     */
    public boolean isSpeechConfigured() {
        return speech != null;
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
