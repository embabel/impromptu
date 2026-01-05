package com.embabel.impromptu.proposition;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.neo.drivine.DrivineNamedEntityDataRepository;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.resolver.NamedEntityDataRepositoryEntityResolver;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionExtractor;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.extraction.LlmPropositionExtractor;
import com.embabel.dice.proposition.revision.LlmPropositionReviser;
import com.embabel.dice.proposition.revision.PropositionReviser;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.data.openopus.graph.ComposerNode;
import com.embabel.impromptu.data.openopus.graph.WorkNode;
import com.embabel.impromptu.user.ImpromptuUser;
import org.drivine.manager.PersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for proposition extraction from chat conversations.
 * Sets up the dice pipeline components for extracting and storing propositions.
 * Enables async processing for non-blocking proposition extraction.
 */
@Configuration
@EnableAsync
class PropositionConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PropositionConfiguration.class);

    /**
     * Schema for the music domain - defines entity types the LLM will extract.
     */
    @Bean
    @Primary
    DataDictionary musicSchema() {
        var schema = DataDictionary.fromClasses(
                ComposerNode.class,
                WorkNode.class,
//                MusicDomainTypes.Instrument.class,
                MusicDomainTypes.MusicPlace.class,
//                MusicDomainTypes.MusicalConcept.class,
                ImpromptuUser.class
        );
        logger.info("Created music domain schema with {} types", schema.getDomainTypes().size());
        return schema;
    }

    /**
     * Embedding service for vector similarity search on propositions.
     */
    @Bean
    EmbeddingService propositionEmbeddingService(AiBuilder aiBuilder) {
        return aiBuilder.ai().withDefaultEmbeddingService();
    }

    /**
     * LLM-based proposition extractor using the dice library.
     */
    @Bean
    LlmPropositionExtractor llmPropositionExtractor(
            AiBuilder aiBuilder,
            PropositionRepository propositionRepository,
            ImpromptuProperties impromptuProperties) {
        var ai = aiBuilder
                .withShowPrompts(impromptuProperties.showExtractionPrompts())
                .withShowLlmResponses(impromptuProperties.showExtractionResponses())
                .ai();
        logger.info("Creating LlmPropositionExtractor with model: {}", impromptuProperties.propositionExtractionLlm());
        return LlmPropositionExtractor
                .withLlm(impromptuProperties.propositionExtractionLlm())
                .withAi(ai)
                .withPropositionRepository(propositionRepository)
                .withLockToSchema(true)
                .withTemplate("dice/extract_impromptu_user_propositions");
    }

    @Bean
    NamedEntityDataRepository namedEntityDataRepository(
            PersistenceManager persistenceManager,
            EmbeddingService embeddingService,
            DataDictionary dataDictionary,
            ImpromptuProperties impromptuProperties) {
        return new DrivineNamedEntityDataRepository(
                persistenceManager,
                impromptuProperties.neoRag(),
                dataDictionary,
                embeddingService
        );
    }

    @Bean
    EntityResolver entityResolver(NamedEntityDataRepository repository) {
        return new NamedEntityDataRepositoryEntityResolver(repository);
    }

    /**
     * The complete proposition extraction pipeline.
     */
    @Bean
    PropositionPipeline propositionPipeline(
            PropositionExtractor propositionExtractor,
            PropositionReviser propositionReviser,
            PropositionRepository propositionRepository
    ) {
        logger.info("Building proposition extraction pipeline");
        return PropositionPipeline
                .withExtractor(propositionExtractor)
                .withRevision(propositionReviser, propositionRepository);
    }

    @Bean
    PropositionReviser propositionReviser(
            AiBuilder aiBuilder,
            ImpromptuProperties impromptuProperties) {
        var ai = aiBuilder
                .withShowPrompts(impromptuProperties.showExtractionPrompts())
                .withShowLlmResponses(impromptuProperties.showExtractionResponses())
                .ai();
        return LlmPropositionReviser
                .withLlm(impromptuProperties.propositionExtractionLlm())
                .withAi(ai);
    }
}
