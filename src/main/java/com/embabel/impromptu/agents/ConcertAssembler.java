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
import com.embabel.agent.api.tool.Tool;
import com.embabel.impromptu.domain.performance.Concert;
import com.embabel.impromptu.domain.performance.ConcertPlan;
import com.embabel.impromptu.domain.performance.Performance;
import com.embabel.impromptu.integrations.coordination.PerformanceAssemblyService;
import com.embabel.impromptu.user.ImpromptuUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Agent that assembles a Concert by finding performances for each work in a plan.
 * <p>
 * Uses structured actions with predictable flow:
 * 1. Extract works from the concert plan
 * 2. Find performances for each work (parallel, using platform search tools)
 * 3. Assemble the final Concert
 * <p>
 * Unlike pure AgenticTool, this provides more predictable behavior while
 * still leveraging tools for the search phase.
 * <p>
 * Input is a {@link ConcertPlan} - the platform is determined from the user's
 * linked services (Spotify takes priority if linked, otherwise YouTube).
 */
@Agent(description = "Assembles a playable concert by finding recordings for each work")
public record ConcertAssembler(
        PerformanceAssemblyService performanceAssemblyService
) {

    // TODO why does this use the blackboard at all

    private static final Logger logger = LoggerFactory.getLogger(ConcertAssembler.class);
    private static final int MAX_CONCURRENCY = 4;

    /**
     * A single work that needs a performance found.
     */
    public record WorkToFind(
            String composer,
            String title,
            String platform
    ) {
        public String searchQuery() {
            return composer + " " + title;
        }
    }

    /**
     * Wrapper for list of works to find.
     * Avoids returning a raw List from an action, which causes issues with ToolObject.
     */
    public record WorksToFind(
            List<WorkToFind> works
    ) {
    }

    /**
     * Simple result returned by LLM after searching for a performance.
     * Does not contain Performance objects (which are polymorphic and can't be deserialized).
     * The actual Performance is stored on the blackboard by the create tool.
     */
    public record PerformanceSearchResult(
            boolean found,
            String message
    ) {
    }

    /**
     * Result of finding a performance for a work.
     * Contains actual Performance objects retrieved from blackboard.
     */
    public record FoundPerformance(
            WorkToFind work,
            Performance<?> performance
    ) {
        public static FoundPerformance success(WorkToFind work, Performance<?> performance) {
            return new FoundPerformance(work, performance);
        }

        public static FoundPerformance notFound(WorkToFind work) {
            return new FoundPerformance(work, null);
        }

        public boolean found() {
            return performance != null;
        }
    }

    /**
     * All performances found (or not) for the concert.
     */
    public record PerformanceFindings(
            List<FoundPerformance> findings
    ) {
        public List<Performance<?>> successfulPerformances() {
            return findings.stream()
                    .filter(FoundPerformance::found)
                    .map(FoundPerformance::performance)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
        }

        public List<WorkToFind> missingWorks() {
            return findings.stream()
                    .filter(f -> !f.found())
                    .map(FoundPerformance::work)
                    .toList();
        }
    }

    /**
     * Determine the platform to use based on user's linked services.
     * Spotify takes priority if linked, otherwise YouTube.
     */
    private String determinePlatform(ImpromptuUser user) {
        if (performanceAssemblyService.isSpotifyAvailable(user)) {
            return "spotify";
        } else if (performanceAssemblyService.isYouTubeAvailable()) {
            return "youtube";
        } else {
            throw new IllegalStateException("No streaming platform available for user");
        }
    }

    /**
     * Extract works to find from the concert plan.
     * Platform is determined from the user's linked services.
     */
    @Action
    public WorksToFind extractWorks(ConcertPlan plan, ImpromptuUser user) {
        var platform = determinePlatform(user);

        logger.info("Extracting {} works to find on {} for '{}'",
                plan.works().size(), platform, plan.name());

        var works = plan.works().stream()
                .map(work -> new WorkToFind(work.composer(), work.title(), platform))
                .toList();
        return new WorksToFind(works);
    }

    /**
     * Find performances for all works in parallel.
     * Uses platform-specific search tools from PerformanceAssemblyService.
     */
    @Action
    public PerformanceFindings findPerformances(
            WorksToFind worksToFind,
            ImpromptuUser user,
            OperationContext context) {

        var works = worksToFind.works();

        // Get search tools for the user's platform
        var searchTools = performanceAssemblyService.tools(user);

        logger.info("Finding performances for {} works using {} tools on {}",
                works.size(), searchTools.size(), works.isEmpty() ? "unknown" : works.get(0).platform());

        if (searchTools.isEmpty()) {
            logger.warn("No search tools available! Spotify available: {}, YouTube available: {}",
                    performanceAssemblyService.isSpotifyAvailable(user),
                    performanceAssemblyService.isYouTubeAvailable());
        } else {
            logger.info("Available search tools: {}", searchTools.stream().map(t -> t.getDefinition().getName()).toList());
        }

        var findings = context.parallelMap(works, MAX_CONCURRENCY, work -> {
            logger.info(">>> Starting search for: {}", work.title());
            var result = findPerformanceForWork(work, searchTools, context);
            logger.info("<<< Finished search for: {} found={}", work.title(), result.found());
            return result;
        });

        var found = findings.stream().filter(FoundPerformance::found).count();
        logger.info("Found {}/{} performances", found, works.size());

        return new PerformanceFindings(findings);
    }

    private FoundPerformance findPerformanceForWork(
            WorkToFind work,
            List<Tool> searchTools,
            OperationContext context) {

        logger.info("Searching for: {} by {} on {}", work.title(), work.composer(), work.platform());

        if (searchTools.isEmpty()) {
            logger.warn("No search tools available for platform: {}", work.platform());
            return FoundPerformance.notFound(work);
        }

        try {
            // Track existing performances before search to find newly added ones
            var blackboard = context.getProcessContext().getBlackboard();
            var existingPerformances = new java.util.HashSet<>(blackboard.objectsOfType(Performance.class));

            // LLM uses tools to search and create performance (stored on blackboard)
            var result = context.ai()
                    .withDefaultLlm()
                    .withTools(searchTools)
                    .creating(PerformanceSearchResult.class)
                    .fromPrompt("""
                            Find the best recording of "%s" by %s on %s.
                            
                            Steps:
                            1. Search for recordings using the search tool
                            2. Pick ONE high-quality recording with:
                               - Complete performance (all movements)
                               - Respected performers/label
                               - Good sound quality
                            3. Create a performance using the create performance tool
                            4. Return found=true if successful
                            
                            You MUST call the create performance tool to complete this task.
                            """.formatted(work.title(), work.composer(), work.platform()));

            logger.info("Search result for {}: found={}, message={}",
                    work.title(), result.found(), result.message());

            // Find the newly added performance that matches this work
            // Filter by work name to handle parallel execution correctly
            var newPerformance = blackboard.objectsOfType(Performance.class).stream()
                    .filter(p -> !existingPerformances.contains(p))
                    .filter(p -> matchesWork(p, work))
                    .findFirst()
                    .orElse(null);

            if (newPerformance != null) {
                logger.info("Found performance for {}: {}", work.title(), newPerformance.title());
                return FoundPerformance.success(work, newPerformance);
            } else {
                logger.warn("No performance found on blackboard for: {}", work.searchQuery());
                return FoundPerformance.notFound(work);
            }
        } catch (Exception e) {
            logger.error("Error finding performance for " + work.searchQuery(), e);
            return FoundPerformance.notFound(work);
        }
    }

    /**
     * Check if a performance matches the work we're searching for.
     * Uses fuzzy matching since LLM-generated work names may differ slightly.
     */
    private boolean matchesWork(Performance performance, WorkToFind work) {
        if (performance.workName() == null) {
            return false;
        }
        var perfName = performance.workName().toLowerCase();
        var workTitle = work.title().toLowerCase();

        // Check if either contains a significant portion of the other
        // Use first 15 chars or full title if shorter to handle variations
        var shortTitle = workTitle.substring(0, Math.min(15, workTitle.length()));
        return perfName.contains(shortTitle) || workTitle.contains(perfName.substring(0, Math.min(15, perfName.length())));
    }

    /**
     * Assemble the final Concert from found performances.
     */
    @AchievesGoal(description = "Assemble a playable concert from performances")
    @Action
    public Concert assembleConcert(ConcertPlan plan, PerformanceFindings findings) {
        var performances = findings.successfulPerformances();
        var missing = findings.missingWorks();

        if (performances.isEmpty()) {
            throw new IllegalStateException("No performances found for any works");
        }

        if (!missing.isEmpty()) {
            logger.warn("Missing performances for {} works: {}",
                    missing.size(),
                    missing.stream().map(WorkToFind::title).toList());
        }

        var concert = Concert.named(plan.name(), performances);

        logger.info("Assembled concert '{}' with {}/{} performances on {}",
                concert.title(),
                performances.size(),
                plan.works().size(),
                concert.platform());

        return concert;
    }
}
