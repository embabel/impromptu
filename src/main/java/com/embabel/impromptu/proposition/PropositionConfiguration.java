package com.embabel.impromptu.proposition;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.neo.drivine.DrivineNamedEntityDataRepository;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.dice.common.*;
import com.embabel.dice.common.resolver.BakeoffPromptStrategies;
import com.embabel.dice.common.resolver.EscalatingEntityResolver;
import com.embabel.dice.common.resolver.LlmCandidateBakeoff;
import com.embabel.dice.common.support.InMemorySchemaRegistry;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionExtractor;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.extraction.LlmPropositionExtractor;
import com.embabel.dice.proposition.revision.LlmPropositionReviser;
import com.embabel.dice.proposition.revision.PropositionReviser;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.domain.Composer;
import com.embabel.impromptu.domain.MusicPlace;
import com.embabel.impromptu.domain.Performer;
import com.embabel.impromptu.domain.Work;
import com.embabel.impromptu.user.ImpromptuUser;
import org.drivine.manager.GraphObjectManager;
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
                Composer.class,
                Work.class,
                Performer.class,
//                MusicDomainTypes.Instrument.class,
                MusicPlace.class,
//                MusicDomainTypes.MusicalConcept.class,
                ImpromptuUser.class
        );
        logger.info("Created music domain schema with {} types", schema.getDomainTypes().size());
        return schema;
    }

    /**
     * Schema registry for DICE REST API.
     * Wraps the default music schema and allows named schema lookup.
     */
    @Bean
    SchemaRegistry schemaRegistry(DataDictionary musicSchema) {
        var registry = new InMemorySchemaRegistry(musicSchema);
        registry.register("music", musicSchema);
        logger.info("Created SchemaRegistry with default music schema");
        return registry;
    }

    @Bean
    Relations relations() {
        return Relations.empty()
                .withPredicatesForSubject(
                        ImpromptuUser.class, KnowledgeType.SEMANTIC,
                        "loves", "likes", "dislikes", "knows", "is_interested_in"
                );
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
                .withSchemaAdherence(SchemaAdherence.DEFAULT)
                .withTemplate("dice/extract_impromptu_user_propositions");
    }

    @Bean
    NamedEntityDataRepository namedEntityDataRepository(
            PersistenceManager persistenceManager,
            EmbeddingService embeddingService,
            GraphObjectManager graphObjectManager,
            DataDictionary dataDictionary,
            ImpromptuProperties impromptuProperties) {
        return new DrivineNamedEntityDataRepository(
                persistenceManager,
                impromptuProperties.neoRag(),
                dataDictionary,
                embeddingService,
                graphObjectManager
        );
    }

    /**
     * Hierarchical entity resolver that escalates through resolution levels:
     * 1. EXACT_MATCH - ID/name lookup (no LLM)
     * 2. HEURISTIC_MATCH - Fuzzy strategies (no LLM)
     * 3. EMBEDDING_MATCH - High-confidence vector (no LLM)
     * 4. LLM_VERIFICATION - Single candidate yes/no
     * 5. LLM_BAKEOFF - Compare multiple candidates
     * <p>
     * This minimizes LLM calls by handling easy cases with fast heuristics.
     */
    @Bean
    EntityResolver entityResolver(
            NamedEntityDataRepository repository,
            AiBuilder aiBuilder,
            ImpromptuProperties impromptuProperties) {
        var llmOptions = impromptuProperties.entityResolutionLlm();
        var ai = aiBuilder
                .withShowPrompts(impromptuProperties.showExtractionPrompts())
                .withShowLlmResponses(impromptuProperties.showExtractionResponses())
                .ai();

        // LLM bakeoff with full prompts (includes descriptions for accurate disambiguation)
        // Use BakeoffPromptStrategies.COMPACT for faster/cheaper resolution if descriptions aren't needed
        var llmBakeoff = LlmCandidateBakeoff
                .withLlm(llmOptions)
                .withAi(ai)
                .withPromptStrategy(BakeoffPromptStrategies.FULL);

        logger.info("Creating EscalatingEntityResolver with model: {}", llmOptions.getModel());
        // Uses default searcher chain: ByIdCandidateSearcher -> ByExactNameCandidateSearcher
        // -> NormalizedNameCandidateSearcher -> PartialNameCandidateSearcher
        // -> FuzzyNameCandidateSearcher -> VectorCandidateSearcher
        return EscalatingEntityResolver.create(repository, llmBakeoff);
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
