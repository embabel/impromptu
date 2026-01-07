package com.embabel.impromptu.proposition.extraction;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.Conversation;
import com.embabel.chat.SimpleMessageFormatter;
import com.embabel.chat.WindowingConversationFormatter;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.Relations;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.NamedEntityDataRepositoryEntityResolver;
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
 * Adapts conversation exchanges to the dice proposition pipeline.
 * Created as a bean in PropositionConfiguration.
 */
@Service
public class ConversationPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPropositionExtraction.class);

    private final PropositionPipeline propositionPipeline;
    private final DataDictionary dataDictionary;
    private final Relations relations;
    private final PropositionRepository propositionRepository;
    private final NamedEntityDataRepository entityRepository;
    private final ImpromptuProperties properties;

    public ConversationPropositionExtraction(
            PropositionPipeline propositionPipeline,
            DataDictionary dataDictionary,
            Relations relations,
            PropositionRepository propositionRepository,
            NamedEntityDataRepository entityRepository,
            ImpromptuProperties properties) {
        this.propositionRepository = propositionRepository;
        this.entityRepository = entityRepository;
        this.relations = relations;
        this.propositionPipeline = propositionPipeline;
        this.dataDictionary = dataDictionary;
        this.properties = properties;
    }

    /**
     * Async event listener for conversation exchanges.
     * Extracts propositions in a separate thread to avoid blocking chat responses.
     * Transaction is managed within the async thread.
     */
    @Async
    @Transactional
    @EventListener
    public void onConversationExchange(ConversationAnalysisRequestEvent event) {
        extractPropositions(event);
    }

    private EntityResolver entityResolverForUser(ImpromptuUser user) {
        return new NamedEntityDataRepositoryEntityResolver(entityRepository);
    }

    /**
     * Extract propositions from a conversation.
     * This builds up a knowledge base from the dialogue.
     */
    public void extractPropositions(ConversationAnalysisRequestEvent event) {
        try {
            var messages = event.conversation.getMessages();
            if (messages.size() < 2) {
                logger.debug("Not enough messages for extraction");
                return;
            }

            // Build context for extraction: combine user question and assistant response
            var extractionText = buildExtractionText(event.conversation);

            var chunk = Chunk.create(
                    extractionText,
                    event.conversation.getId(),
                    Map.of(
                            "source", "conversation",
                            "conversationId", event.conversation.getId()
                    )
            );

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

            logger.info("Extracting propositions from conversation exchange");

            var chunkPropositionResult = propositionPipeline.processChunk(chunk, context);

            if (!chunkPropositionResult.getPropositions().isEmpty()) {
                var resolvedCount = chunkPropositionResult.getPropositions().stream()
                        .filter(ReferencesEntities::isFullyResolved)
                        .count();

                logger.info(chunkPropositionResult.infoString(true, 1));
                // Calculate actual counts based on what will be persisted
                var propsToSave = chunkPropositionResult.propositionsToPersist();
                var referencedEntityIds = propsToSave.stream()
                        .flatMap(p -> p.getMentions().stream())
                        .map(EntityMention::getResolvedId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
                var newEntitiesToSave = chunkPropositionResult.newEntities().stream()
                        .filter(e -> referencedEntityIds.contains(e.getId()))
                        .count();

                // Get revision stats for accurate logging
                var stats = chunkPropositionResult.getPropositionExtractionStats();
                var newProps = stats.getNewCount();
                var updatedProps = stats.getMergedCount() + stats.getReinforcedCount();

                chunkPropositionResult.persist(propositionRepository, entityRepository);
                if (newProps > 0 || updatedProps > 0 || newEntitiesToSave > 0) {
                    logger.info("Persisted: {} new propositions, {} updated propositions, {} new entities",
                            newProps,
                            updatedProps,
                            newEntitiesToSave
                    );
                } else {
                    logger.info("No new data to persist (all propositions were duplicates)");
                }
            }
        } catch (Exception e) {
            // Don't let extraction failures break the chat flow
            logger.warn("Failed to extract propositions", e);
        }
    }


    private String buildExtractionText(Conversation conversation) {
        var windowSize = properties.extraction().windowSize();
        return new WindowingConversationFormatter(SimpleMessageFormatter.INSTANCE, windowSize)
                .format(conversation);
    }
}
