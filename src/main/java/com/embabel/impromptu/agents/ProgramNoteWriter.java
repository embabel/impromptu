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
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.library.InternetResource;
import com.embabel.impromptu.domain.performance.Concert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes program notes for a concert, researching composers, works, and performers in parallel.
 * <p>
 * Flow:
 * 1. Extract topics from the concert (composers, works, performers)
 * 2. Research each topic in parallel using web search
 * 3. Write the final program notes in XHTML format
 */
@Agent(description = "Writes program notes for a concert program")
public class ProgramNoteWriter {

    private static final Logger logger = LoggerFactory.getLogger(ProgramNoteWriter.class);
    private static final int MAX_CONCURRENCY = 8;

    /**
     * Request to write program notes for a concert.
     *
     * @param concert       the concert to write notes for
     * @param audience      description of the target audience (e.g., "general concert-goers", "music students")
     * @param style         writing style (e.g., "scholarly", "accessible", "entertaining")
     * @param wordCount     approximate word count for the final notes
     */
    public record ProgramNoteRequest(
            Concert concert,
            String audience,
            String style,
            int wordCount
    ) {
        public ProgramNoteRequest(Concert concert) {
            this(concert, "general concert-goers", "accessible and engaging", 1500);
        }
    }

    /**
     * The final program notes content (markdown format).
     * Can be converted to XHTML/PDF using ResourceGenerationService.
     */
    public record ProgramNotes(
            String title,
            String content,
            List<InternetResource> references
    ) {
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
     * Extract topics from the concert that need research.
     */
    @Action
    public ProgramTopics extractTopics(ProgramNoteRequest request) {
        var concert = request.concert();

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
            ProgramNoteRequest request,
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
                .withDefaultLlm()
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
                .withDefaultLlm()
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
                .withDefaultLlm()
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
                .withDefaultLlm()
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
     * Output is structured markdown content that can be converted to XHTML/PDF
     * using ResourceGenerationService.
     */
    @AchievesGoal(description = "Write program notes for a concert")
    @Action
    public ProgramNotes writeProgramNotes(
            ProgramNoteRequest request,
            ResearchFindings research,
            OperationContext context) {

        logger.info("Writing program notes for: {}", request.concert().title());

        var concert = request.concert();
        var researchSummary = formatResearchForPrompt(research);

        return context.ai()
                .withDefaultLlm()
                .creating(ProgramNotes.class)
                .fromPrompt("""
                        Write program notes for the following concert in markdown format.

                        Concert: %s
                        Works:
                        %s

                        Target audience: %s
                        Style: %s
                        Approximate word count: %d

                        Research findings:
                        %s

                        Guidelines:
                        - Write engaging, informative program notes
                        - Include interesting anecdotes and historical context
                        - Help the audience appreciate what they're about to hear
                        - Suggest what to listen for in each piece
                        - Use markdown formatting with headings (##, ###), paragraphs, and lists
                        - Start with ## for each work section
                        - Do NOT include a top-level title (it will be added separately)

                        Set 'title' to a suitable title for the program notes.
                        Set 'content' to the full markdown content (without the title).
                        Include all references from the research in the 'references' field.
                        """.formatted(
                        concert.title(),
                        formatWorksForPrompt(concert),
                        request.audience(),
                        request.style(),
                        request.wordCount(),
                        researchSummary));
    }

    private String formatWorksForPrompt(Concert concert) {
        var sb = new StringBuilder();
        for (int i = 0; i < concert.performances().size(); i++) {
            var perf = concert.performances().get(i);
            sb.append(i + 1).append(". ");
            if (perf.composer() != null) {
                sb.append(perf.composer()).append(": ");
            }
            sb.append(perf.workName() != null ? perf.workName() : perf.title());
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
