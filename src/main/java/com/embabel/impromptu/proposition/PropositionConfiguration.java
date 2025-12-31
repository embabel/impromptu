package com.embabel.impromptu.proposition;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.dice.pipeline.PropositionBuilders;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.extraction.LlmPropositionExtractor;
import com.embabel.dice.proposition.store.InMemoryPropositionRepository;
import com.embabel.dice.text2graph.resolver.InMemoryEntityResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for proposition extraction from chat conversations.
 * Sets up the dice pipeline components for extracting and storing propositions.
 * Enables async processing for non-blocking proposition extraction.
 */
@Configuration
@EnableAsync
public class PropositionConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PropositionConfiguration.class);

    /**
     * Schema for the music domain - defines entity types the LLM will extract.
     */
    @Bean
    public DataDictionary musicSchema() {
        var schema = DataDictionary.fromClasses(
                MusicDomainTypes.Composer.class,
                MusicDomainTypes.MusicalWork.class,
                MusicDomainTypes.Critic.class,
                MusicDomainTypes.Genre.class,
                MusicDomainTypes.Instrument.class,
                MusicDomainTypes.MusicPlace.class,
                MusicDomainTypes.MusicalConcept.class,
                MusicDomainTypes.ChatUser.class
        );
        logger.info("Created music domain schema with {} types", schema.getDomainTypes().size());
        return schema;
    }

    /**
     * Embedding service for vector similarity search on propositions.
     */
    @Bean
    public EmbeddingService propositionEmbeddingService(AiBuilder aiBuilder) {
        return aiBuilder.ai().withDefaultEmbeddingService();
    }

    /**
     * Repository for storing and querying propositions.
     */
    @Bean
    public PropositionRepository propositionRepository(EmbeddingService propositionEmbeddingService) {
        logger.info("Creating InMemoryPropositionRepository");
        return new InMemoryPropositionRepository(propositionEmbeddingService);
    }

    /**
     * LLM-based proposition extractor using the dice library.
     */
    @Bean
    public LlmPropositionExtractor llmPropositionExtractor(AiBuilder aiBuilder) {
        var ai = aiBuilder
                .withShowPrompts(false)
                .withShowLlmResponses(false)
                .ai();
        // Use a fast model for extraction to minimize latency
        var llmOptions = LlmOptions.withModel("gpt-4.1-mini");
        logger.info("Creating LlmPropositionExtractor with model: {}", llmOptions.getModel());
        return new LlmPropositionExtractor(ai, llmOptions);
    }

    /**
     * The complete proposition extraction pipeline.
     */
    @Bean
    public PropositionPipeline propositionPipeline(
            LlmPropositionExtractor llmPropositionExtractor,
            PropositionRepository propositionRepository) {
        logger.info("Building proposition extraction pipeline");
        return PropositionBuilders
                .withExtractor(llmPropositionExtractor)
                .withEntityResolver(new InMemoryEntityResolver())
                .withStore(propositionRepository)
                .build();
    }

    /**
     * Async event listener that adapts conversations to the proposition pipeline.
     */
    @Bean
    public PropositionExtractor propositionExtractor(
            PropositionPipeline propositionPipeline,
            DataDictionary musicSchema) {
        return new PropositionExtractor(propositionPipeline, musicSchema);
    }
}
