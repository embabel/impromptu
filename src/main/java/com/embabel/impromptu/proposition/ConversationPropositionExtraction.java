package com.embabel.impromptu.proposition;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.chat.Conversation;
import com.embabel.chat.SimpleMessageFormatter;
import com.embabel.chat.WindowingConversationFormatter;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.InMemoryEntityResolver;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.ReferencesEntities;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Map;

/**
 * Async listener that extracts propositions from chat conversations.
 * Adapts conversation exchanges to the dice proposition pipeline.
 * Created as a bean in PropositionConfiguration.
 */
public class ConversationPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPropositionExtraction.class);

    private final PropositionPipeline propositionPipeline;
    private final DataDictionary musicSchema;

    public ConversationPropositionExtraction(
            PropositionPipeline propositionPipeline,
            DataDictionary musicSchema) {
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
        // TODO this is wrong
        return new InMemoryEntityResolver();
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

            var context = new SourceAnalysisContext(
                    musicSchema,
                    entityResolverForUser(event.user),
                    List.of(
                            event.user.toKnownEntity()
                    ),
                    Map.of(
                            // Whatever
                    )
            );

            logger.info("Extracting propositions from conversation exchange");

            var result = propositionPipeline.processChunk(chunk, context);

            if (!result.getPropositions().isEmpty()) {
                var resolvedCount = result.getPropositions().stream()
                        .filter(ReferencesEntities::isFullyResolved)
                        .count();

                logger.info(
                        "Extracted {} propositions from conversation (resolved: {}, new: {})",
                        result.getPropositions().size(),
                        resolvedCount,
                        result.getNewCount()
                );

                // Log a summary of what was learned
                result.getPropositions().stream()
                        .limit(3)
                        .forEach(prop -> logger.debug("  - {} (confidence: {})", prop.getText(), prop.getConfidence()));

                if (result.getPropositions().size() > 3) {
                    logger.debug("  ... and {} more", result.getPropositions().size() - 3);
                }
            }
        } catch (Exception e) {
            // Don't let extraction failures break the chat flow
            logger.warn("Failed to extract propositions: {}", e.getMessage());
            logger.debug("Extraction error details", e);
        }
    }

    private String buildExtractionText(Conversation conversation) {
        // TODO deal with hardcoding
        return new WindowingConversationFormatter(SimpleMessageFormatter.INSTANCE, 10)
                .format(conversation);
    }
}
