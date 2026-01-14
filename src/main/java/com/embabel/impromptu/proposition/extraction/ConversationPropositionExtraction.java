package com.embabel.impromptu.proposition.extraction;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.Message;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.Relations;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.KnownEntityResolver;
import com.embabel.dice.incremental.*;
import com.embabel.dice.incremental.proposition.PropositionIncrementalAnalyzer;
import com.embabel.dice.pipeline.ChunkPropositionResult;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.ReferencesEntities;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.event.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * Async listener that extracts propositions from chat conversations.
 * Uses IncrementalAnalyzer for windowed, deduplicated processing.
 */
@Service
public class ConversationPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPropositionExtraction.class);

    private final IncrementalAnalyzer<Message, ChunkPropositionResult> analyzer;
    private final DataDictionary dataDictionary;
    private final Relations relations;
    private final PropositionRepository propositionRepository;
    private final NamedEntityDataRepository entityRepository;
    private final EntityResolver entityResolver;

    public ConversationPropositionExtraction(
            PropositionPipeline propositionPipeline,
            ChunkHistoryStore chunkHistoryStore,
            DataDictionary dataDictionary,
            Relations relations,
            PropositionRepository propositionRepository,
            NamedEntityDataRepository entityRepository,
            EntityResolver entityResolver,
            ImpromptuProperties properties) {
        this.dataDictionary = dataDictionary;
        this.relations = relations;
        this.propositionRepository = propositionRepository;
        this.entityRepository = entityRepository;
        this.entityResolver = entityResolver;

        // Configure analyzer with properties
        var extraction = properties.extraction();
        var config = new WindowConfig(
                extraction.windowSize(),
                extraction.overlapSize(),
                extraction.triggerInterval()
        );
        this.analyzer = new PropositionIncrementalAnalyzer<>(
                propositionPipeline,
                chunkHistoryStore,
                MessageFormatter.INSTANCE,
                config
        );
    }

    /**
     * Async event listener for conversation exchanges.
     * Extracts propositions in a separate thread to avoid blocking chat responses.
     */
    @Async
    @Transactional
    @EventListener
    public void onConversationExchange(ConversationAnalysisRequestEvent event) {
        extractPropositions(event);
    }

    private EntityResolver entityResolverForUser(ImpromptuUser user) {
        // Wrap the resolver with the user as a known entity
        return KnownEntityResolver.withKnownEntities(
                java.util.List.of(KnownEntity.asCurrentUser(user)),
                entityResolver
        );
    }

    /**
     * Extract propositions from a conversation.
     * The analyzer decides if there's enough new content to process.
     */
    public void extractPropositions(ConversationAnalysisRequestEvent event) {
        logger.info("Starting proposition extraction for conversation with {} messages",
                event.conversation.getMessages().size());
        try {
            var messages = event.conversation.getMessages();
            if (messages.size() < 2) {
                logger.info("Not enough messages for extraction (need at least 2)");
                return;
            }

            // Build context for extraction
            var context = SourceAnalysisContext
                    .withContextId(event.user.currentContext())
                    .withEntityResolver(entityResolverForUser(event.user))
                    .withSchema(dataDictionary)
                    .withRelations(relations)
                    .withKnownEntities(
                            KnownEntity.asCurrentUser(event.user)
                    )
                    .withPromptVariables(Map.of(
                            "user", event.user
                    ));

            // Wrap conversation as incremental source and analyze
            var source = new ConversationSource(event.conversation);
            var result = analyzer.analyze(source, context);

            if (result == null) {
                logger.info("Analysis skipped (not ready or already processed)");
                return;
            }

            if (result.getPropositions().isEmpty()) {
                logger.info("Analysis completed but no propositions extracted");
                return;
            }
            var resolvedCount = result.getPropositions().stream()
                    .filter(ReferencesEntities::isFullyResolved)
                    .count();

            logger.info(result.infoString(true, 1));

            // Calculate actual counts based on what will be persisted
            var propsToSave = result.propositionsToPersist();
            var referencedEntityIds = propsToSave.stream()
                    .flatMap(p -> p.getMentions().stream())
                    .map(EntityMention::getResolvedId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            var newEntitiesToSave = result.newEntities().stream()
                    .filter(e -> referencedEntityIds.contains(e.getId()))
                    .count();

            // Get revision stats for accurate logging
            var stats = result.getPropositionExtractionStats();
            var newProps = stats.getNewCount();
            var updatedProps = stats.getMergedCount() + stats.getReinforcedCount();

            result.persist(propositionRepository, entityRepository);
            if (newProps > 0 || updatedProps > 0 || newEntitiesToSave > 0) {
                logger.info("Persisted: {} new propositions, {} updated propositions, {} new entities",
                        newProps,
                        updatedProps,
                        newEntitiesToSave
                );
            } else {
                logger.info("No new data to persist (all propositions were duplicates)");
            }
        } catch (Exception e) {
            // Don't let extraction failures break the chat flow
            logger.warn("Failed to extract propositions", e);
        }
    }
}
