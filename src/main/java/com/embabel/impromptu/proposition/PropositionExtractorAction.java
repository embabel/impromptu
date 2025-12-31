package com.embabel.impromptu.proposition;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.text2graph.builder.SourceAnalysisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Action that extracts propositions from chat conversations.
 * Triggered after user messages to learn facts from the dialogue.
 */
@EmbabelComponent
public class PropositionExtractorAction {

    private static final Logger logger = LoggerFactory.getLogger(PropositionExtractorAction.class);

    private final PropositionPipeline propositionPipeline;
    private final PropositionRepository propositionRepository;
    private final DataDictionary musicSchema;

    public PropositionExtractorAction(
            PropositionPipeline propositionPipeline,
            PropositionRepository propositionRepository,
            DataDictionary musicSchema) {
        this.propositionPipeline = propositionPipeline;
        this.propositionRepository = propositionRepository;
        this.musicSchema = musicSchema;
    }

    /**
     * Extract propositions after user messages.
     * This builds up a knowledge base from the conversation.
     * Only runs every 5 messages to avoid excessive LLM calls.
     */
    @Action(
            canRerun = true,
            trigger = UserMessage.class,
            pre = "spel:#conversation.messages.size() % 5 == 2"
    )
    public void extractPropositions(Conversation conversation, ActionContext context) {
        try {
            var messages = conversation.getMessages();
            if (messages.size() < 2) {
                logger.debug("Not enough messages for extraction");
                return;
            }

            // Find the most recent user message and assistant response
            Message lastAssistantMsg = null;
            Message lastUserMsg = null;

            for (int i = messages.size() - 1; i >= 0; i--) {
                var msg = messages.get(i);
                if (lastAssistantMsg == null && msg instanceof AssistantMessage) {
                    lastAssistantMsg = msg;
                }
                if (lastUserMsg == null && msg instanceof UserMessage) {
                    lastUserMsg = msg;
                }
                if (lastAssistantMsg != null && lastUserMsg != null) {
                    break;
                }
            }

            if (lastUserMsg == null || lastAssistantMsg == null) {
                logger.debug("Missing user or assistant message");
                return;
            }

            // Build context for extraction: combine user question and assistant response
            var extractionText = buildExtractionText(lastUserMsg, lastAssistantMsg);

            // Create a chunk from the conversation exchange using companion object
            var chunk = Chunk.Companion.invoke(
                    UUID.randomUUID().toString(),
                    extractionText,
                    Map.of(
                            "source", "conversation",
                            "conversationId", conversation.getId()
                    ),
                    conversation.getId()
            );

            var sourceConfig = new SourceAnalysisConfig(
                    musicSchema,
                    """
                    Extract facts about music, composers, musical works, and user interests.
                    Focus on:
                    - Facts about composers and their works mentioned in the conversation
                    - Musical concepts or terms discussed
                    - What topics the user is interested in or asking about
                    - Any relationships between musical entities (composer wrote work, work is in genre, etc.)

                    For user interests, create propositions like "The user is interested in [topic]"
                    based on what they are asking about.
                    """
            );

            logger.debug("Extracting propositions from conversation exchange");

            var result = propositionPipeline.processChunk(chunk, sourceConfig);

            if (!result.getPropositions().isEmpty()) {
                var resolvedCount = result.getPropositions().stream()
                        .filter(p -> p.isFullyResolved())
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

    private String buildExtractionText(Message userMsg, Message assistantMsg) {
        return """
                User asked: %s

                Assistant response: %s
                """.formatted(userMsg.getContent(), assistantMsg.getContent());
    }
}
