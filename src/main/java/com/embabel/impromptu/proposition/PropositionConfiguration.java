package com.embabel.impromptu.proposition;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.neo.drivine.DrivineNamedEntityDataRepository;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnowledgeType;
import com.embabel.dice.common.Relations;
import com.embabel.dice.common.SchemaAdherence;
import com.embabel.dice.common.resolver.ContextCompressor;
import com.embabel.dice.common.resolver.HierarchicalConfig;
import com.embabel.dice.common.resolver.HierarchicalEntityResolver;
import com.embabel.dice.common.resolver.MatchStrategyKt;
import com.embabel.dice.common.resolver.matcher.LlmCandidateBakeoff;
import com.embabel.dice.common.resolver.matcher.PromptMode;
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
     *
     * This minimizes LLM calls by handling easy cases with fast heuristics.
     */
    @Bean
    EntityResolver entityResolver(
            NamedEntityDataRepository repository,
            ModelProvider modelProvider,
            ImpromptuProperties impromptuProperties) {
        // Get model name from LlmOptions, with fallback to default
        var llmOptions = impromptuProperties.entityResolutionLlm();
        var modelName = llmOptions.getModel() != null ? llmOptions.getModel() : "gpt-4.1-mini";

        // LLM bakeoff with compact prompts (~100 tokens vs ~400 for full)
        var llmBakeoff = new LlmCandidateBakeoff(
                modelProvider,
                modelName,
                PromptMode.COMPACT
        );

        // Hierarchical config - tune thresholds for music domain
        var config = new HierarchicalConfig(
                0.95,   // embeddingAutoAcceptThreshold - high confidence = auto-accept
                0.7,    // embeddingCandidateThreshold - consider as candidate
                10,     // topK - max candidates to retrieve
                true,   // useTextSearch
                true,   // useVectorSearch
                false,  // heuristicOnly - allow LLM for ambiguous cases
                0.9     // earlyTerminationThreshold
        );

        logger.info("Creating HierarchicalEntityResolver with model: {}", modelName);
        return new HierarchicalEntityResolver(
                repository,
                MatchStrategyKt.defaultMatchStrategies(),
                llmBakeoff,
                ContextCompressor.none(),  // Context compressed separately in pipeline
                config
        );
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
