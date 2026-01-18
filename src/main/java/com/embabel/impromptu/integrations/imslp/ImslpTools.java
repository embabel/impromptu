/*
 * Copyright 2024-2025 Embabel Software, Inc.
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
package com.embabel.impromptu.integrations.imslp;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM tools for IMSLP (International Music Score Library Project) integration.
 * <p>
 * Uses {@link MatryoshkaTools} for progressive tool disclosure. The LLM first sees
 * an "imslp" facade tool. When invoked, all IMSLP-specific tools become available.
 * <p>
 * IMSLP is the Petrucci Music Library, containing over 600,000 public domain music scores.
 *
 * @see <a href="https://imslp.org/">IMSLP - Petrucci Music Library</a>
 */
@MatryoshkaTools(
        name = "imslp",
        description = "Access IMSLP (International Music Score Library Project) to find and download " +
                "public domain music scores. Invoke this tool to search for sheet music by composer " +
                "and work title, browse available editions, and get PDF download links."
)
public record ImslpTools(
        ImslpService imslpService
) {

    private static final Logger logger = LoggerFactory.getLogger(ImslpTools.class);

    /**
     * Default instance with a new service.
     */
    public static final ImslpTools DEFAULT = new ImslpTools(new ImslpService());

    /**
     * Find a musical score on IMSLP by composer and work title.
     */
    @LlmTool(description = """
            Find a musical score on IMSLP given composer and work title.

            COMPOSER FORMAT: Use last name only (e.g., 'Brahms', 'Bach', 'Beethoven').
            IMSLP page titles use format: Work_Title_(Composer,_First_Last)

            WORK TITLE FORMAT: Use standard titles with proper spacing:
            - 'Symphony No. 5' (not 'Symphony No.5')
            - 'Violin Sonata No. 1, Op. 78' or just 'Violin Sonata Op. 78'
            - 'The Well-Tempered Clavier' or 'Well-Tempered Clavier'

            TROUBLESHOOTING - if no results:
            1. Simplify: Try just 'Violin Sonata' instead of 'Violin Sonata No. 1 in G major'
            2. Try searchWorks with a flexible query like 'Brahms violin sonata'
            3. Different opus formats: 'Op. 78' vs 'Op.78' vs 'Opus 78'

            ALWAYS share the IMSLP page URL with the user - they can browse editions directly.
            """)
    public String findScore(String composer, String workTitle) {
        try {
            var result = imslpService.findScore(composer, workTitle);

            if (result.workPageUrl() == null) {
                return "No scores found on IMSLP for \"" + workTitle + "\" by " + composer + ". " +
                        "Try adjusting the search terms or use searchWorks for a broader search.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(workTitle).append(" by ").append(composer).append("\n\n");
            sb.append("[View on IMSLP](").append(result.workPageUrl()).append(")\n\n");

            if (result.hasScores()) {
                sb.append("### Available Scores\n\n");
                int count = 0;
                for (var score : result.pdfScores()) {
                    count++;
                    sb.append(count).append(". **").append(cleanFilename(score.filename())).append("**");
                    if (score.description() != null) {
                        sb.append(" - ").append(score.description());
                    }
                    sb.append(" (").append(score.displaySize()).append(")\n");
                    sb.append("   [Download PDF](").append(score.url()).append(")\n\n");
                }

                if (result.pdfScores().size() >= 10) {
                    sb.append("*Note: Showing first 10 scores. Visit the IMSLP page for more editions.*\n");
                }
            } else {
                sb.append("No PDF scores found on this page. The work may have scores in other formats ");
                sb.append("or linked from sub-pages. Visit the IMSLP page directly for more details.\n");
            }

            logger.info("Found {} scores for '{}' by '{}'", result.pdfScores().size(), workTitle, composer);
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to find score for '{}' by '{}': {}", workTitle, composer, e.getMessage());
            return "Failed to search IMSLP: " + e.getMessage();
        }
    }

    /**
     * Search for works on IMSLP with a general query.
     */
    @LlmTool(description = """
            Search IMSLP with a flexible free-text query. More forgiving than findScore.

            USE THIS WHEN:
            - findScore returns no results
            - You're not sure of the exact work title
            - User wants to browse what's available
            - Looking for arrangements, transcriptions, or lesser-known works

            GOOD QUERIES:
            - 'Brahms violin sonata' (finds all violin sonatas)
            - 'Bach cello suite' (finds the cello suites)
            - 'Mozart piano concerto K.467'
            - 'Chopin nocturne' (finds all nocturnes)

            ALWAYS share the IMSLP links with the user so they can click through directly.
            The links work - don't say you 'cannot provide links'.
            """)
    public String searchWorks(String query) {
        try {
            var results = imslpService.searchWorks(query, 10);

            if (results.isEmpty()) {
                return "No results found on IMSLP for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## IMSLP Search Results for \"").append(query).append("\"\n\n");

            int count = 0;
            for (var result : results) {
                count++;
                sb.append(count).append(". **").append(result.pageTitle()).append("**\n");
                if (result.snippet() != null && !result.snippet().isBlank()) {
                    sb.append("   ").append(truncate(result.snippet(), 150)).append("\n");
                }
                sb.append("   [View on IMSLP](").append(result.pageUrl()).append(")\n\n");
            }

            sb.append("Use findScore with a specific composer and work title to get PDF download links.\n");

            logger.debug("IMSLP search for '{}' returned {} results", query, results.size());
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to search IMSLP for '{}': {}", query, e.getMessage());
            return "Failed to search IMSLP: " + e.getMessage();
        }
    }

    /**
     * Search for works by a specific composer.
     */
    @LlmTool(description = """
            Browse works by a specific composer on IMSLP.

            Use just the composer's last name: 'Beethoven', 'Chopin', 'Debussy'.

            Returns a sample of their works - IMSLP has thousands of works for major composers.
            Use findScore or searchWorks for specific pieces.

            Share the links with the user so they can explore IMSLP directly.
            """)
    public String searchByComposer(String composer) {
        try {
            // Search specifically for works by this composer
            var results = imslpService.searchWorks(composer, 15);

            if (results.isEmpty()) {
                return "No works found on IMSLP for composer: " + composer;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Works by ").append(composer).append(" on IMSLP\n\n");

            int count = 0;
            for (var result : results) {
                // Filter to likely be actual works (pages with parentheses usually indicate works)
                String title = result.pageTitle();
                if (title.toLowerCase().contains(composer.toLowerCase().split(",")[0].split(" ")[0])) {
                    count++;
                    sb.append(count).append(". **").append(title).append("**\n");
                    sb.append("   [View](").append(result.pageUrl()).append(")\n\n");
                }
            }

            if (count == 0) {
                // Fallback: show all results
                for (var result : results) {
                    count++;
                    sb.append(count).append(". **").append(result.pageTitle()).append("**\n");
                    sb.append("   [View](").append(result.pageUrl()).append(")\n\n");
                    if (count >= 10) break;
                }
            }

            sb.append("Use findScore to get PDF download links for a specific work.\n");
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to search IMSLP for composer '{}': {}", composer, e.getMessage());
            return "Failed to search IMSLP: " + e.getMessage();
        }
    }

    /**
     * Get information about IMSLP and its capabilities.
     */
    @LlmTool(description = "Get information about IMSLP and what's available")
    public String getImslpInfo() {
        return """
                ## About IMSLP (International Music Score Library Project)

                IMSLP, also known as the Petrucci Music Library, is the largest collection of free \
                public domain music scores on the internet.

                **What's Available:**
                - Over 600,000 music scores
                - 80,000+ works by 20,000+ composers
                - Primarily public domain classical music
                - Includes full scores, parts, piano reductions, and arrangements

                **Copyright Notes:**
                - Most scores are in the public domain in countries with life+70 or shorter copyright terms
                - Some scores may still be under copyright in certain countries
                - Always check the copyright status on the score page

                **Available Tools:**
                - `findScore(composer, workTitle)` - Get PDF links for a specific work
                - `searchWorks(query)` - General search across all works
                - `searchByComposer(composer)` - Find works by a specific composer

                **Tips:**
                - Use standard composer names (e.g., "Bach", "Beethoven", "Mozart")
                - Include opus numbers when known (e.g., "Piano Sonata Op. 13")
                - Multiple editions are often available - check the IMSLP page for all options
                """;
    }

    /**
     * Clean up IMSLP filename for display.
     */
    private String cleanFilename(String filename) {
        if (filename == null) return "Unknown";
        return filename
                .replace("_", " ")
                .replaceAll("\\.(pdf|PDF)$", "")
                .replaceAll("^PMLP\\d+[-_]", ""); // Remove PMLP prefix
    }

    /**
     * Truncate text to max length.
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
