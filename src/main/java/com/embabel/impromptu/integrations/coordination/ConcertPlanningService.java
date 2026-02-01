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

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.tool.agentic.playbook.PlaybookTool;
import com.embabel.agent.rag.neo.drivine.cyphergen.CypherQueryTools;
import com.embabel.impromptu.domain.performance.ConcertPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for planning concert programs.
 * <p>
 * Uses the Open Opus database to select appropriate works for a concert,
 * considering duration, programming conventions, and user preferences.
 * This is the fast planning phase - no external API calls to streaming services.
 * <p>
 * The user confirms the plan before the slower performance-finding phase.
 */
@Service
public class ConcertPlanningService {

    private static final Logger logger = LoggerFactory.getLogger(ConcertPlanningService.class);
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/concert/planning_system.jinja";

    private final ResourceLoader resourceLoader;
    private final CypherQueryTools cypherQueryTools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConcertPlanningService(ResourceLoader resourceLoader, CypherQueryTools cypherQueryTools) {
        this.resourceLoader = resourceLoader;
        this.cypherQueryTools = cypherQueryTools;
    }

    private String loadSystemPrompt() {
        try {
            var resource = resourceLoader.getResource(SYSTEM_PROMPT_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load system prompt from {}", SYSTEM_PROMPT_PATH, e);
            throw new IllegalStateException("Failed to load concert planning system prompt", e);
        }
    }

    /**
     * Create a PlaybookTool for concert planning.
     * <p>
     * Uses progressive tool unlocking to ensure the LLM queries the database
     * before attempting to create a plan. This prevents hallucinated works.
     * <p>
     * This tool plans the program but doesn't search for recordings.
     * The result is a ConcertPlan for user confirmation.
     */
    public Tool createConcertPlanningTool() {
        var queryTool = cypherQueryTools.tool("""
                Query the music database for composers and works. \
                The database contains the Open Opus catalog with composers, works, \
                genres (Orchestral, Chamber, Keyboard, Vocal, Stage), \
                and epochs (Baroque, Classical, Romantic, 20th Century, etc.). \
                Example queries: works by a composer, works of a specific genre, \
                popular works, works from an era.""");

        return new PlaybookTool(
                "planConcert",
                """
                        Plan a concert program. Returns a ConcertPlan with suggested works \
                        for user confirmation. Fast - no streaming service searches. \
                        Call this first, then use assembleConcert after user confirms."""
        )
                .withTools(queryTool, createPlanTool())
                .withSystemPrompt(loadSystemPrompt())
                .withParameter(Tool.Parameter.string(
                        "request",
                        "Required. What the user wants: composers, works, themes, style, mood."
                ))
                .withParameter(Tool.Parameter.integer(
                        "durationMinutes",
                        "Required. Target duration in minutes."
                ));
    }

    /**
     * Tool to create the final ConcertPlan.
     */
    private Tool createPlanTool() {
        return Tool.create(
                "createPlan",
                """
                        Create a ConcertPlan with your selected works. \
                        Call this after deciding which works to include.""",
                Tool.InputSchema.of(
                        Tool.Parameter.string("name", "Concert name/title"),
                        Tool.Parameter.string("works",
                                "JSON array of works: [{\"composer\":\"...\",\"title\":\"...\",\"estimatedMinutes\":N,\"notes\":\"...\"}]"),
                        Tool.Parameter.string("rationale", "Brief explanation of programming choices", false)
                ),
                input -> {
                    try {
                        var params = objectMapper.readValue(input, java.util.Map.class);
                        var name = (String) params.get("name");
                        var worksJson = (String) params.get("works");
                        var rationale = (String) params.get("rationale");

                        if (name == null || name.isBlank()) {
                            return Tool.Result.text("Error: name is required");
                        }
                        if (worksJson == null || worksJson.isBlank()) {
                            return Tool.Result.text("Error: works is required");
                        }

                        // Parse works JSON
                        var worksList = objectMapper.readValue(worksJson, List.class);
                        var plannedWorks = new ArrayList<ConcertPlan.PlannedWork>();
                        int totalMinutes = 0;

                        for (var workObj : worksList) {
                            var work = (java.util.Map<String, Object>) workObj;
                            var composer = (String) work.get("composer");
                            var title = (String) work.get("title");
                            var minutes = ((Number) work.get("estimatedMinutes")).intValue();
                            var notes = (String) work.get("notes");

                            plannedWorks.add(new ConcertPlan.PlannedWork(composer, title, minutes, notes));
                            totalMinutes += minutes;
                        }

                        var plan = new ConcertPlan(name, plannedWorks, rationale, totalMinutes);

                        logger.info("Created concert plan '{}' with {} works (~{} min)",
                                name, plannedWorks.size(), totalMinutes);

                        return Tool.Result.withArtifact(
                                plan.formatProgram(),
                                plan
                        );
                    } catch (Exception e) {
                        logger.error("Failed to create concert plan", e);
                        return Tool.Result.text("Error creating plan: " + e.getMessage());
                    }
                }
        );
    }
}
