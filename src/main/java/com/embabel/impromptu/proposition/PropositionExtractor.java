package com.embabel.impromptu.proposition;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.chat.Conversation;
import com.embabel.chat.SimpleMessageFormatter;
import com.embabel.chat.WindowingConversationFormatter;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.resolver.InMemoryEntityResolver;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.ReferencesEntities;
import com.embabel.dice.t.SourceAnalysisConfig;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;

/**
 * Async listener that extracts propositions from chat conversations.
 * Adapts conversation exchanges to the dice proposition pipeline.
 * Created as a bean in PropositionConfiguration.
 */
public class PropositionExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PropositionExtractor.class);

    private final PropositionPipeline propositionPipeline;
    private final DataDictionary musicSchema;

    public PropositionExtractor(
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

            var sourceConfig = new SourceAnalysisConfig(
                    musicSchema,
                    entityResolverForUser(event.user),
                    """
                            Extract facts about the user and the user's musical preferences:
                            
                            - The user's level of knowledge
                            - Composers, works, genres, and historical periods mentioned
                            - The users's preferences or opinions expressed about such things
                            - Musical concepts or terms discussed
                            - What topics the user is interested in or asking about
                            
                            For user interests, create propositions like "The user dislikes Baroque music"
                            or "The user is interested in learning about Romantic composers" or "The user's favorite composer is Messiaen".
                            based on what they are asking about.
                            
                            You are not noting known facts from general knowledge:
                            GOOD: "The user discussed Brahms in detail"
                            BAD: "Brahms was a Romantic composer"
                            GOOD: "The user enjoys atonal music"
                            BAD: "Atonal music lacks a tonal center"
                            
                            DO NOT ADD ANYTHING NOT SUPPORTED BY THE CONVERSATION TEXT.
                            """
            );

            logger.debug("Extracting propositions from conversation exchange");

            var result = propositionPipeline.processChunk(chunk, sourceConfig);

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
