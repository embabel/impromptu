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
package com.embabel.impromptu.integrations.coordination;

import com.embabel.agent.api.tool.AgenticTool;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentProcess;
import com.embabel.impromptu.domain.performance.Concert;
import com.embabel.impromptu.domain.performance.ConcertPlan;
import com.embabel.impromptu.domain.performance.Performance;
import com.embabel.impromptu.user.ImpromptuUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service for assembling concerts from a confirmed plan.
 * <p>
 * Takes a {@link ConcertPlan} (from {@link ConcertPlanningService}) and finds
 * actual performances for each work, then creates a playable {@link Concert}.
 * <p>
 * This is the slower phase that searches streaming services.
 */
@Service
public class ConcertAssemblyService {

    private static final Logger logger = LoggerFactory.getLogger(ConcertAssemblyService.class);
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/concert/assembly_system.jinja";

    private final PerformanceAssemblyService performanceAssemblyService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConcertAssemblyService(PerformanceAssemblyService performanceAssemblyService,
                                  ResourceLoader resourceLoader) {
        this.performanceAssemblyService = performanceAssemblyService;
        this.resourceLoader = resourceLoader;
    }

    private String loadSystemPrompt() {
        try {
            var resource = resourceLoader.getResource(SYSTEM_PROMPT_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load system prompt from {}", SYSTEM_PROMPT_PATH, e);
            throw new IllegalStateException("Failed to load concert assembly system prompt", e);
        }
    }

    /**
     * Create an AgenticTool for concert assembly.
     * <p>
     * Takes a confirmed plan and platform, finds performances, creates Concert.
     *
     * @param user The user (needed for platform access)
     * @return Configured AgenticTool
     */
    public Tool createConcertAssemblyTool(ImpromptuUser user) {
        return new AgenticTool(
                "assembleConcert",
                """
                        Find recordings and create a playable Concert asset. \
                        WORKFLOW: (1) Use planConcert first to propose works. \
                        (2) Once user confirms, call this tool. \
                        (3) If a work can't be found, clarify alternatives with user. \
                        (4) After clarification, call this tool AGAIN with corrected works. \
                        (5) Always complete the workflow - create a Concert asset, don't just provide URLs. \
                        The Concert will appear in assets with play buttons."""
        )
                .withTools(
                        // Use single-result finder that picks ONE best recording per work
                        performanceAssemblyService.createSingleResultPerformanceFinderTool(user),
                        createConcertTool()
                )
                .withSystemPrompt(loadSystemPrompt())
                .withParameter(Tool.Parameter.string(
                        "works",
                        "Required. The works to find, as search queries (e.g., 'Brahms Symphony No. 4')."
                ))
                .withParameter(Tool.Parameter.string(
                        "platform",
                        "Required. Platform to use: 'spotify' or 'youtube'."
                ))
                .withParameter(Tool.Parameter.string(
                        "concertName",
                        "Required. Name for the concert."
                ));
    }

    /**
     * Check if concert assembly is available.
     */
    public boolean isAvailable(ImpromptuUser user) {
        return performanceAssemblyService.isAvailable(user);
    }

    /**
     * Tool to create a Concert from performances stored in the blackboard.
     */
    private Tool createConcertTool() {
        return Tool.create(
                "createConcert",
                """
                        Create a Concert from the performances you've found. \
                        Call this after using findPerformances for all works. \
                        Returns the Concert as a playable artifact.""",
                Tool.InputSchema.of(
                        Tool.Parameter.string("name", "Name for the concert program")
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var name = (String) params.get("name");

                        var process = AgentProcess.get();
                        if (process == null) {
                            return Tool.Result.text("Error: No agent process available");
                        }

                        // Get all performances from the blackboard
                        @SuppressWarnings("unchecked")
                        var performances = (List<Performance<?>>) (List<?>)
                                process.getBlackboard().objectsOfType(Performance.class);

                        if (performances.isEmpty()) {
                            return Tool.Result.text(
                                    "Error: No performances found. Use findPerformances first.");
                        }

                        // Verify all performances are from the same platform
                        var platforms = performances.stream()
                                .map(Performance::source)
                                .distinct()
                                .toList();
                        if (platforms.size() > 1) {
                            return Tool.Result.text(
                                    "Error: Performances are from multiple platforms: " + platforms +
                                            ". All must be from the same platform.");
                        }

                        var concert = Concert.named(
                                name != null ? name : "Concert Program",
                                performances
                        );

                        logger.info("Created concert '{}' with {} performances on {}",
                                concert.title(), concert.size(), concert.platform());

                        return Tool.Result.withArtifact(
                                "Created concert '%s' with %d performances (%s)"
                                        .formatted(concert.title(), concert.size(),
                                                concert.durationFormatted()),
                                concert
                        );
                    } catch (Exception e) {
                        logger.error("Failed to create concert", e);
                        return Tool.Result.text("Error creating concert: " + e.getMessage());
                    }
                }
        );
    }
}
