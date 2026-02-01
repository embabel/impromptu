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
package com.embabel.impromptu.domain.performance;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.chat.Asset;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A planned concert program - the works to be performed, before finding actual recordings.
 * <p>
 * This is the output of the planning phase, which the user can confirm or modify
 * before the system searches for actual performances.
 * <p>
 * Implements {@link Asset} so it can be tracked between conversation turns,
 * allowing the ConcertAssembler to find recordings after user confirmation.
 *
 * @param id                    unique identifier for this plan
 * @param name                  suggested name for the concert
 * @param works                 the planned works in program order
 * @param rationale             brief explanation of the programming choices
 * @param estimatedTotalMinutes approximate total duration (varies by performance)
 * @param timestamp             when the plan was created
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)  // Disable polymorphic typing from Asset/LlmReferenceProvider
public record ConcertPlan(
        String id,
        String name,
        List<PlannedWork> works,
        String rationale,
        int estimatedTotalMinutes,
        Instant timestamp
) implements Asset {

    /**
     * Jackson factory for deserialization - allows missing fields.
     */
    @JsonCreator
    public static ConcertPlan create(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("works") List<PlannedWork> works,
            @JsonProperty("rationale") String rationale,
            @JsonProperty("estimatedTotalMinutes") Integer estimatedTotalMinutes
    ) {
        return new ConcertPlan(
                id != null ? id : UUID.randomUUID().toString(),
                name,
                works,
                rationale,
                estimatedTotalMinutes != null ? estimatedTotalMinutes : 0,
                Instant.now()
        );
    }

    /**
     * Create a ConcertPlan with auto-generated id and current timestamp.
     */
    public ConcertPlan(String name, List<PlannedWork> works, String rationale, int estimatedTotalMinutes) {
        this(UUID.randomUUID().toString(), name, works, rationale, estimatedTotalMinutes, Instant.now());
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
        return false;
    }

    @Override
    @NonNull
    public LlmReference reference() {
        // Create a short ID for tool prefix
        var shortId = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (shortId.length() > 25) {
            shortId = shortId.substring(0, 25);
        }

        // Include JSON that the LLM can pass to ConcertAssembler
        var worksJson = new StringBuilder("[");
        for (int i = 0; i < works.size(); i++) {
            if (i > 0) worksJson.append(",");
            var work = works.get(i);
            worksJson.append(String.format(
                    "{\"composer\":\"%s\",\"title\":\"%s\",\"estimatedMinutes\":%d}",
                    escapeJson(work.composer()),
                    escapeJson(work.title()),
                    work.estimatedMinutes()
            ));
        }
        worksJson.append("]");

        var notes = formatProgram() + "\n\n" +
                "To use this plan with ConcertAssembler, pass:\n" +
                "```json\n" +
                "{\"id\":\"" + id + "\",\"name\":\"" + escapeJson(name) + "\"," +
                "\"works\":" + worksJson + "," +
                "\"estimatedTotalMinutes\":" + estimatedTotalMinutes + "}\n" +
                "```";

        return LlmReference.of(
                shortId,
                "Concert Plan: " + name + " (~" + estimatedTotalMinutes + " min)",
                List.of(),
                notes
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * A work planned for inclusion in the concert.
     *
     * @param composer         composer name
     * @param title            work title (e.g., "Symphony No. 5 in C minor, Op. 67")
     * @param estimatedMinutes approximate duration (will vary by performance)
     * @param notes            optional notes about this choice
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedWork(
            @JsonProperty("composer") String composer,
            @JsonProperty("title") String title,
            @JsonProperty("estimatedMinutes") int estimatedMinutes,
            @JsonProperty("notes") String notes
    ) {

    }

    /**
     * Format as a readable program for user confirmation.
     */
    public String formatProgram() {
        var sb = new StringBuilder();
        sb.append("**").append(name).append("**\n");
        sb.append("(approximately ").append(estimatedTotalMinutes).append(" minutes)\n\n");

        for (int i = 0; i < works.size(); i++) {
            var work = works.get(i);
            sb.append(i + 1).append(". ");
            sb.append(work.composer()).append(": ").append(work.title());
            sb.append(" (~").append(work.estimatedMinutes()).append(" min)");
            if (work.notes() != null && !work.notes().isBlank()) {
                sb.append("\n   _").append(work.notes()).append("_");
            }
            sb.append("\n");
        }

        if (rationale != null && !rationale.isBlank()) {
            sb.append("\n").append(rationale);
        }

        return sb.toString();
    }
}
