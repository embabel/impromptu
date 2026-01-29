/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Properties for chatbot. See application.yml
 *
 * @param chatLlm                  LLM model and hyperparameters to use
 *                                 for chatbot responses
 * @param embeddingService         the name of the embedding service to use
 *                                 for retrieval-augmented generation
 * @param objective                the goal of the chatbot's responses: For example, to answer legal questions
 * @param defaultPersonality       the persona and output style of the chatbot while achieving its objective
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
 * @param dataLoading              configuration for automatic data loading on startup
 */
@ConfigurationProperties(prefix = "impromptu")
public record ImpromptuProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        @NestedConfigurationProperty LlmOptions pdfGenerationLlm,
        @NestedConfigurationProperty LlmOptions cypherGenerationLlm,
        String embeddingService,
        @DefaultValue(DEFAULT) String guardrails,
        @DefaultValue(DEFAULT) String objective,
        @DefaultValue(DEFAULT) String opinions,
        @DefaultValue(DEFAULT) String behaviour,
        @DefaultValue("50") int conversationWindow,
        @NestedConfigurationProperty Personality defaultPersonality,
        @NestedConfigurationProperty Extraction extraction,
        @DefaultValue @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @NestedConfigurationProperty LlmOptions propositionExtractionLlm,
        @NestedConfigurationProperty LlmOptions entityResolutionLlm,
        boolean showExtractionPrompts,
        boolean showExtractionResponses,
        boolean showChatPrompts,
        @Nullable @NestedConfigurationProperty Speech speech,
        @Nullable @NestedConfigurationProperty DataLoading dataLoading
) {

    private static final String DEFAULT = "default";

    public record Personality(
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

    /**
     * Configuration for automatic data loading on startup.
     * Data is loaded in the background after the application starts.
     * Each source is only loaded if its data doesn't already exist.
     *
     * @param openOpus  whether to load Open Opus composer/work data
     * @param documents list of document URLs or file paths to ingest into the RAG store
     */
    public record DataLoading(
            @DefaultValue("false") boolean openOpus,
            @Nullable List<String> documents
    ) {
        public DataLoading {
            if (documents == null) {
                documents = List.of();
            }
        }

        public boolean hasDocuments() {
            return documents != null && !documents.isEmpty();
        }
    }
}
