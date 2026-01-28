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
package com.embabel.impromptu.proposition;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.neo.drivine.CypherQueryTools;
import com.embabel.agent.rag.neo.drivine.DrivineNamedEntityDataRepository;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.dice.common.*;
import com.embabel.dice.common.resolver.BakeoffPromptStrategies;
import com.embabel.dice.common.resolver.EscalatingEntityResolver;
import com.embabel.dice.common.resolver.LlmCandidateBakeoff;
import com.embabel.dice.common.support.InMemorySchemaRegistry;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.projection.graph.GraphProjector;
import com.embabel.dice.projection.graph.GraphRelationshipPersister;
import com.embabel.dice.projection.graph.NamedEntityDataRepositoryGraphRelationshipPersister;
import com.embabel.dice.projection.graph.RelationBasedGraphProjector;
import com.embabel.dice.proposition.PropositionExtractor;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.extraction.LlmPropositionExtractor;
import com.embabel.dice.proposition.revision.LlmPropositionReviser;
import com.embabel.dice.proposition.revision.PropositionReviser;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.domain.*;
import com.embabel.impromptu.integrations.spotify.SpotifyPerformance;
import com.embabel.impromptu.integrations.spotify.SpotifyTrack;
import com.embabel.impromptu.integrations.youtube.YouTubePerformance;
import com.embabel.impromptu.integrations.youtube.YouTubeVideo;
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
                "art_music",
                Nationality.class,
                Epoch.class,
                Composer.class,
                Work.class,
                Technique.class,
                Performer.class,
                Ensemble.class,
                Instrument.class,
                Family.class,
                MusicPlace.class,
                MusicalConcept.class,
                ImpromptuUser.class,
                SpotifyPerformance.class,
                SpotifyTrack.class,
                YouTubePerformance.class,
                YouTubeVideo.class
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
        registry.register(musicSchema);
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

    @Bean
    CypherQueryTools cypherQueryTools(
            DataDictionary musicSchema,
            PersistenceManager persistenceManager,
            ImpromptuProperties properties) {
        return new CypherQueryTools(
                // Don't let it query about users
                // Cypher queries are only intended to help with reference data
                musicSchema.excluding(ImpromptuUser.class),
                persistenceManager,
                properties.cypherGenerationLlm()
        );
    }

    /**
     * Graph projector that creates semantic relationships from propositions.
     * Uses predicate matching (no LLM) to map proposition text to relationship types.
     */
    @Bean
    GraphProjector graphProjector(Relations relations) {
        return RelationBasedGraphProjector.from(relations);
    }

    /**
     * Persister for projected graph relationships.
     */
    @Bean
    GraphRelationshipPersister graphRelationshipPersister(NamedEntityDataRepository repository) {
        return new NamedEntityDataRepositoryGraphRelationshipPersister(repository);
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
