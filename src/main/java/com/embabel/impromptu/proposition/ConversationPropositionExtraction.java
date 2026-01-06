package com.embabel.impromptu.proposition;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.Conversation;
import com.embabel.chat.SimpleMessageFormatter;
import com.embabel.chat.WindowingConversationFormatter;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.NamedEntityDataRepositoryEntityResolver;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.ReferencesEntities;
import com.embabel.impromptu.domain.MusicPlace;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Async listener that extracts propositions from chat conversations.
 * Adapts conversation exchanges to the dice proposition pipeline.
 * Created as a bean in PropositionConfiguration.
 */
@Service
public class ConversationPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPropositionExtraction.class);

    private final PropositionPipeline propositionPipeline;
    private final DataDictionary musicSchema;
    private final PropositionRepository propositionRepository;
    private final NamedEntityDataRepository entityRepository;

    public ConversationPropositionExtraction(
            PropositionPipeline propositionPipeline,
            DataDictionary musicSchema,
            PropositionRepository propositionRepository,
            NamedEntityDataRepository entityRepository) {
        this.propositionRepository = propositionRepository;
        this.entityRepository = entityRepository;
        this.propositionPipeline = propositionPipeline;
        this.musicSchema = musicSchema;
    }

    /**
     * Async event listener for conversation exchanges.
     * Extracts propositions in a separate thread to avoid blocking chat responses.
     */
    @Async
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
    // TODO needs to be transactional
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
                    .withSchema(musicSchema)
                    .withKnownEntities(
                            List.of(event.user)
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

                // Build a pretty-printed summary of extracted propositions
                var props = chunkPropositionResult.getPropositions();
                var sb = new StringBuilder();
                sb.append(String.format("Extracted %d propositions from conversation (resolved: %d, new: %d)",
                        props.size(),
                        resolvedCount,
                        chunkPropositionResult.getPropositionExtractionStats().getNewCount()));
                for (var prop : props) {
                    sb.append(String.format("%n  • %s (confidence: %.2f)", prop.getText(), prop.getConfidence()));
                }
                logger.info(sb.toString());
                // Calculate actual counts based on what will be persisted
                var propsToSave = chunkPropositionResult.propositionsToPersist();
                var referencedEntityIds = propsToSave.stream()
                        .flatMap(p -> p.getMentions().stream())
                        .map(m -> m.getResolvedId())
                        .filter(id -> id != null)
                        .collect(java.util.stream.Collectors.toSet());
                var newEntitiesToSave = chunkPropositionResult.newEntities().stream()
                        .filter(e -> referencedEntityIds.contains(e.getId()))
                        .count();

                // Get revision stats for accurate logging
                var stats = chunkPropositionResult.getPropositionExtractionStats();
                var newProps = stats.getNewCount();
                var updatedProps = stats.getMergedCount() + stats.getReinforcedCount();

                // Actually persist the extracted propositions and entities
                chunkPropositionResult.persist(propositionRepository, entityRepository);
                if (newProps > 0 || updatedProps > 0 || newEntitiesToSave > 0) {
                    logger.info("Persisted: {} new propositions, {} updated propositions, {} new entities",
                            newProps,
                            updatedProps,
                            newEntitiesToSave
                    );

                    // TODO this is just diagnostic
                    var allPlaces = entityRepository.findAll(MusicPlace.class);
                    var placesSb = new StringBuilder();
                    placesSb.append(String.format("Total known places in entity repository: %d", allPlaces.size()));
                    for (var place : allPlaces) {
                        placesSb.append(String.format("%n  • %s", place.infoString(true, 1)));
                    }
                    logger.info(placesSb.toString());

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
        // TODO deal with hardcoding
        return new WindowingConversationFormatter(SimpleMessageFormatter.INSTANCE, 10)
                .format(conversation);
    }
}
