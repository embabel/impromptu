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
package com.embabel.impromptu.agents;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.library.InternetResource;
import com.embabel.chat.Asset;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.domain.performance.Concert;
import com.embabel.impromptu.domain.performance.ConcertPlan;
import com.embabel.impromptu.resource.ResourceDelivery;
import com.embabel.impromptu.resource.ResourceGenerationService;
import com.embabel.impromptu.resource.ResourceRequest;
import com.embabel.impromptu.resource.ResourceResult;
import com.embabel.impromptu.user.ImpromptuUser;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes program notes for a concert, researching composers, works, and performers in parallel.
 * <p>
 * Flow:
 * 1. Extract topics from the concert (composers, works, performers)
 * 2. Research each topic in parallel using web search (fast model)
 * 3. Write the final program notes in XHTML format
 */
@Agent(
        description = "Writes program notes for a concert program",
        opaque = true
)
public class ProgramNoteWriter {

    private static final Logger logger = LoggerFactory.getLogger(ProgramNoteWriter.class);
    private static final int MAX_CONCURRENCY = 8;

    private final ImpromptuProperties properties;
    private final ResourceGenerationService resourceGenerationService;
    private final ResourceDelivery resourceDelivery;

    public ProgramNoteWriter(
            ImpromptuProperties properties,
            ResourceGenerationService resourceGenerationService,
            ResourceDelivery resourceDelivery) {
        this.properties = properties;
        this.resourceGenerationService = resourceGenerationService;
        this.resourceDelivery = resourceDelivery;
    }

    /**
     * Get the LLM to use for research - fast model if configured, otherwise default.
     */
    private LlmOptions researchLlm() {
        return properties.researchLlm() != null
                ? properties.researchLlm()
                : LlmOptions.withDefaults();
    }

    /**
     * Simple concert info that can be serialized/deserialized without polymorphism issues.
     * This is the data we need from a Concert to write program notes.
     */
    public record ConcertInfo(
            String title,
            List<String> composers,
            List<PerformanceInfo> performances,
            List<String> performers,
            List<String> conductors,
            List<String> ensembles
    ) {
        /**
         * Information about a single performance in the concert.
         */
        public record PerformanceInfo(
                String composer,
                String workName,
                String performer
        ) {}
    }

    /**
     * Request to write program notes for a concert.
     * The concert data is retrieved from the blackboard (set by ChatActions).
     * This record contains only the user-configurable options.
     *
     * @param audience      description of the target audience (e.g., "general concert-goers", "music students")
     * @param style         writing style (e.g., "scholarly", "accessible", "entertaining")
     * @param wordsPerWork  approximate word count per work (500-600 words = 1 page)
     */
    public record ProgramNoteRequest(
            String audience,
            String style,
            int wordsPerWork
    ) {
        public ProgramNoteRequest() {
            this("general concert-goers", "accessible and engaging", 550);
        }
    }

    /**
     * Simple content record that the LLM can create (no polymorphic annotations).
     */
    public record ProgramNotesContent(
            String title,
            String content,
            List<InternetResource> references
    ) {}

    /**
     * The final program notes (markdown format).
     * Implements Asset so it can be tracked and referenced in conversations.
     * Created by Java code from ProgramNotesContent - NOT directly by LLM.
     */
    public record ProgramNotes(
            String id,
            String title,
            String content,
            List<InternetResource> references,
            String resourceUrl,
            Instant timestamp
    ) implements Asset {

        /**
         * Create from LLM-generated content with resource URL.
         */
        public static ProgramNotes from(ProgramNotesContent content, String resourceUrl) {
            return new ProgramNotes(
                    UUID.randomUUID().toString(),
                    content.title(),
                    content.content(),
                    content.references(),
                    resourceUrl,
                    Instant.now()
            );
        }

        @Override
        @NonNull
        public String getId() {
            return id;
        }

        @Override
        @NonNull
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean persistent() {
            return true;
        }

        @Override
        @NonNull
        public LlmReference reference() {
            var shortId = title.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_|_$", "");
            if (shortId.length() > 25) {
                shortId = shortId.substring(0, 25);
            }

            var tools = new ArrayList<Tool>();

            // Add link to view/download the formatted notes
            if (resourceUrl != null && !resourceUrl.isBlank()) {
                var url = resourceUrl;  // capture for lambda
                tools.add(Tool.create(
                        "view_program_notes",
                        "Open the formatted program notes: " + resourceUrl,
                        input -> Tool.Result.text(
                                "{\"type\":\"document\",\"title\":\"" + title.replace("\"", "\\\"") +
                                        "\",\"url\":\"" + url + "\"}")
                ));
            }

            // Build description with link
            var description = title + " [Program Notes]";
            if (resourceUrl != null && !resourceUrl.isBlank()) {
                description += " - [View](" + resourceUrl + ")";
            }

            return LlmReference.of(
                    shortId,
                    description,
                    tools,
                    content
            );
        }

        /**
         * Format as content suitable for ResourceGenerationService.
         */
        public String toResourceContent() {
            var sb = new StringBuilder();
            sb.append("# ").append(title).append("\n\n");
            sb.append(content);
            if (references != null && !references.isEmpty()) {
                sb.append("\n\n## References\n");
                for (var ref : references) {
                    sb.append("- [").append(ref.getSummary()).append("](").append(ref.getUrl()).append(")\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Topics extracted from the concert for research.
     */
    public record ProgramTopics(
            List<String> composers,
            List<WorkTopic> works,
            List<String> performers,
            List<String> concepts
    ) {
    }

    /**
     * A work to research, with composer context.
     */
    public record WorkTopic(
            String composer,
            String workName,
            String performer
    ) {
    }

    /**
     * Research findings for a single topic.
     */
    public record TopicResearch(
            String topic,
            String category,
            String summary,
            List<String> keyPoints,
            List<InternetResource> references
    ) {
    }

    /**
     * All research findings collected before writing.
     */
    public record ResearchFindings(
            List<TopicResearch> composerResearch,
            List<TopicResearch> workResearch,
            List<TopicResearch> performerResearch,
            List<TopicResearch> conceptResearch
    ) {
        public List<InternetResource> allReferences() {
            var refs = new ArrayList<InternetResource>();
            composerResearch.forEach(r -> refs.addAll(r.references()));
            workResearch.forEach(r -> refs.addAll(r.references()));
            performerResearch.forEach(r -> refs.addAll(r.references()));
            conceptResearch.forEach(r -> refs.addAll(r.references()));
            return refs;
        }
    }

    /**
     * Bind the Concert from the parent blackboard and convert to ConcertInfo.
     * The Concert and ConcertPlan are placed in the blackboard by ChatActions before invoking this subagent.
     * Uses the ConcertPlan to fill in composer info that may be missing from Performance objects.
     */
    @Action
    public ConcertInfo bindConcert(OperationContext context) {
        var concert = context.last(Concert.class);
        if (concert == null) {
            logger.error("No Concert found in blackboard");
            throw new IllegalStateException("No Concert found - create a concert first");
        }
        logger.info("Bound concert from blackboard: {}", concert.title());

        // Get ConcertPlan for composer info (Performances from Spotify/YouTube may not have it)
        var concertPlan = context.last(ConcertPlan.class);
        var composerByWork = new java.util.HashMap<String, String>();
        if (concertPlan != null) {
            logger.info("Using ConcertPlan for composer info: {} works", concertPlan.works().size());
            for (var work : concertPlan.works()) {
                // Use lowercase for matching since titles may vary slightly
                composerByWork.put(work.title().toLowerCase(), work.composer());
            }
        }

        // Convert Concert to ConcertInfo (simple serializable form)
        // Use ConcertPlan composer info when Performance doesn't have it
        var performances = concert.performances().stream()
                .map(perf -> {
                    var composer = perf.composer();
                    if (composer == null && perf.workName() != null) {
                        composer = composerByWork.get(perf.workName().toLowerCase());
                    }
                    return new ConcertInfo.PerformanceInfo(
                            composer,
                            perf.workName(),
                            perf.performer()
                    );
                })
                .collect(Collectors.toList());

        // Build composer list from performances (may now have data from ConcertPlan)
        var composers = performances.stream()
                .map(ConcertInfo.PerformanceInfo::composer)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());

        return new ConcertInfo(
                concert.title(),
                composers,
                performances,
                concert.performers(),
                concert.conductors(),
                concert.ensembles()
        );
    }

    /**
     * Extract topics from the concert that need research.
     */
    @Action
    public ProgramTopics extractTopics(ConcertInfo concert) {

        // Extract unique composers
        var composers = concert.composers();

        // Extract works with their composers and performers
        var works = new ArrayList<WorkTopic>();
        for (var perf : concert.performances()) {
            works.add(new WorkTopic(
                    perf.composer(),
                    perf.workName(),
                    perf.performer()
            ));
        }

        // Extract unique performers (soloists, conductors, ensembles)
        var performers = new ArrayList<String>();
        performers.addAll(concert.performers());
        performers.addAll(concert.conductors());
        performers.addAll(concert.ensembles());
        var uniquePerformers = performers.stream()
                .distinct()
                .collect(Collectors.toList());

        // Concepts could be extracted by LLM analysis of the program
        // For now, leave empty - could be enhanced later
        var concepts = List.<String>of();

        logger.info("Extracted topics - composers: {}, works: {}, performers: {}",
                composers.size(), works.size(), uniquePerformers.size());

        return new ProgramTopics(composers, works, uniquePerformers, concepts);
    }

    /**
     * Research all topics in parallel.
     */
    @Action
    public ResearchFindings researchTopics(
            ProgramTopics topics,
            OperationContext context) {

        logger.info("Starting parallel research on {} composers, {} works, {} performers",
                topics.composers().size(),
                topics.works().size(),
                topics.performers().size());

        // Research composers in parallel
        var composerResearch = context.parallelMap(
                topics.composers(),
                MAX_CONCURRENCY,
                composer -> researchComposer(composer, context)
        );

        // Research works in parallel
        var workResearch = context.parallelMap(
                topics.works(),
                MAX_CONCURRENCY,
                work -> researchWork(work, context)
        );

        // Research performers in parallel
        var performerResearch = context.parallelMap(
                topics.performers(),
                MAX_CONCURRENCY,
                performer -> researchPerformer(performer, context)
        );

        // Concepts research (if any)
        var conceptResearch = context.parallelMap(
                topics.concepts(),
                MAX_CONCURRENCY,
                concept -> researchConcept(concept, context)
        );

        return new ResearchFindings(
                composerResearch,
                workResearch,
                performerResearch,
                conceptResearch
        );
    }

    private TopicResearch researchComposer(String composer, OperationContext context) {
        logger.debug("Researching composer: {}", composer);
        return context.ai()
                .withLlm(researchLlm())
                .withTools(CoreToolGroups.WEB)
                .creating(TopicResearch.class)
                .fromPrompt("""
                        Research the composer %s for concert program notes.

                        Focus on:
                        - Brief biographical context relevant to their music
                        - Their musical style and significance
                        - Interesting anecdotes that would engage concert audiences
                        - Historical context and influences

                        Use web search to find authoritative sources.
                        Provide 3-5 key points suitable for program notes.
                        Include references to your sources.

                        Set category to "composer".
                        """.formatted(composer));
    }

    private TopicResearch researchWork(WorkTopic work, OperationContext context) {
        logger.debug("Researching work: {} by {}", work.workName(), work.composer());
        return context.ai()
                .withLlm(researchLlm())
                .withTools(CoreToolGroups.WEB)
                .creating(TopicResearch.class)
                .fromPrompt("""
                        Research the musical work "%s" by %s for concert program notes.

                        Focus on:
                        - When and why it was composed (commission, dedication, circumstances)
                        - Structure and notable musical features
                        - Historical reception and significance
                        - What to listen for (themes, memorable moments)
                        - Any connection to the performer %s if relevant

                        Use web search to find authoritative sources.
                        Provide 3-5 key points suitable for program notes.
                        Include references to your sources.

                        Set category to "work".
                        Set topic to "%s: %s".
                        """.formatted(
                        work.workName(),
                        work.composer() != null ? work.composer() : "Unknown",
                        work.performer() != null ? work.performer() : "the performers",
                        work.composer() != null ? work.composer() : "Unknown",
                        work.workName()));
    }

    private TopicResearch researchPerformer(String performer, OperationContext context) {
        logger.debug("Researching performer: {}", performer);
        return context.ai()
                .withLlm(researchLlm())
                .withTools(CoreToolGroups.WEB)
                .creating(TopicResearch.class)
                .fromPrompt("""
                        Research the performer/ensemble %s for concert program notes.

                        Focus on:
                        - Background and musical training
                        - Notable achievements and recordings
                        - Their approach or style
                        - Connection to the repertoire on this program if known

                        Use web search to find authoritative sources.
                        Provide 2-3 key points suitable for program notes.
                        Include references to your sources.

                        Set category to "performer".
                        """.formatted(performer));
    }

    private TopicResearch researchConcept(String concept, OperationContext context) {
        logger.debug("Researching concept: {}", concept);
        return context.ai()
                .withLlm(researchLlm())
                .withTools(CoreToolGroups.WEB)
                .creating(TopicResearch.class)
                .fromPrompt("""
                        Research the musical concept "%s" for concert program notes.

                        Explain it in accessible terms for concert audiences.
                        Use web search to verify information.

                        Set category to "concept".
                        """.formatted(concept));
    }

    /**
     * Write the final program notes combining all research.
     * Output is structured markdown content that is converted to XHTML
     * using ResourceGenerationService.
     */
    @AchievesGoal(description = "Write program notes for a concert")
    @Action
    public ProgramNotes writeProgramNotes(
            ConcertInfo concert,
            ProgramNoteRequest request,
            ResearchFindings research,
            ImpromptuUser user,
            OperationContext context) {

        logger.info("Writing program notes for: {}", concert.title());
        var researchSummary = formatResearchForPrompt(research);
        int totalWords = request.wordsPerWork() * concert.performances().size();

        // LLM creates simple ProgramNotesContent using Jinja template
        // The template handles persona integration via {% include %}
        var notesContent = context.ai()
                .withDefaultLlm()
                .withTemplate("concert/program_notes")
                .createObject(ProgramNotesContent.class, Map.of(
                        "user", user,
                        "concert", concert,
                        "wordsPerWork", request.wordsPerWork(),
                        "totalWords", totalWords,
                        "research", researchSummary
                ));

        // Generate XHTML resource from the content
        String resourceUrl = null;
        try {
            var markdownContent = "# " + notesContent.title() + "\n\n" + notesContent.content();
            var resourceRequest = new ResourceRequest(
                    "Program Notes: " + concert.title(),
                    markdownContent
            );
            var xhtmlResult = resourceGenerationService.generateXhtmlOnly(resourceRequest);
            var resourceId = resourceDelivery.store(
                    new ResourceResult(xhtmlResult.xhtml().getBytes(), xhtmlResult.filename()));
            resourceUrl = resourceDelivery.getLocation(resourceId).orElse(null);
            logger.info("Generated XHTML resource for program notes: {}", resourceUrl);
        } catch (Exception e) {
            logger.warn("Failed to generate XHTML resource for program notes: {}", e.getMessage());
            // Continue without resource URL - notes will still be available as text
        }

        // Convert to Asset-implementing ProgramNotes (done in Java, not by LLM)
        return ProgramNotes.from(notesContent, resourceUrl);
    }

    private String formatWorksForPrompt(ConcertInfo concert) {
        var sb = new StringBuilder();
        for (int i = 0; i < concert.performances().size(); i++) {
            var perf = concert.performances().get(i);
            sb.append(i + 1).append(". ");
            if (perf.composer() != null) {
                sb.append(perf.composer()).append(": ");
            }
            sb.append(perf.workName() != null ? perf.workName() : "Unknown work");
            if (perf.performer() != null) {
                sb.append(" (").append(perf.performer()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatResearchForPrompt(ResearchFindings research) {
        var sb = new StringBuilder();

        if (!research.composerResearch().isEmpty()) {
            sb.append("## Composers\n");
            for (var r : research.composerResearch()) {
                sb.append("### ").append(r.topic()).append("\n");
                sb.append(r.summary()).append("\n");
                if (r.keyPoints() != null) {
                    for (var point : r.keyPoints()) {
                        sb.append("- ").append(point).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        if (!research.workResearch().isEmpty()) {
            sb.append("## Works\n");
            for (var r : research.workResearch()) {
                sb.append("### ").append(r.topic()).append("\n");
                sb.append(r.summary()).append("\n");
                if (r.keyPoints() != null) {
                    for (var point : r.keyPoints()) {
                        sb.append("- ").append(point).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        if (!research.performerResearch().isEmpty()) {
            sb.append("## Performers\n");
            for (var r : research.performerResearch()) {
                sb.append("### ").append(r.topic()).append("\n");
                sb.append(r.summary()).append("\n");
                if (r.keyPoints() != null) {
                    for (var point : r.keyPoints()) {
                        sb.append("- ").append(point).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
