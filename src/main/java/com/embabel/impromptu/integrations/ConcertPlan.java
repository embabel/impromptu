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
package com.embabel.impromptu.integrations;

import java.util.List;

/**
 * A planned concert program - the works to be performed, before finding actual recordings.
 * <p>
 * This is the output of the planning phase, which the user can confirm or modify
 * before the system searches for actual performances.
 *
 * @param name suggested name for the concert
 * @param works the planned works in program order
 * @param rationale brief explanation of the programming choices
 * @param estimatedTotalMinutes approximate total duration (varies by performance)
 */
public record ConcertPlan(
        String name,
        List<PlannedWork> works,
        String rationale,
        int estimatedTotalMinutes
) {

    /**
     * A work planned for inclusion in the concert.
     *
     * @param composer composer name
     * @param title work title (e.g., "Symphony No. 5 in C minor, Op. 67")
     * @param estimatedMinutes approximate duration (will vary by performance)
     * @param notes optional notes about this choice
     */
    public record PlannedWork(
            String composer,
            String title,
            int estimatedMinutes,
            String notes
    ) {
        public PlannedWork(String composer, String title, int estimatedMinutes) {
            this(composer, title, estimatedMinutes, null);
        }

        /**
         * Search query to find this work.
         */
        public String searchQuery() {
            return composer + " " + title;
        }
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
