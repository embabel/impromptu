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

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.tool.Tool;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A concert program consisting of multiple performances from a single platform.
 * <p>
 * A Concert aggregates performances into a cohesive program. All performances
 * must be from the same platform (Spotify or YouTube) to enable seamless playback.
 * <p>
 * Example: A "Brahms Evening" concert on Spotify might include:
 * <ul>
 *   <li>Symphony No. 1 - Berlin Philharmonic</li>
 *   <li>Violin Concerto - Heifetz</li>
 *   <li>Piano Concerto No. 2 - Zimerman</li>
 * </ul>
 */
public record Concert(
        String id,
        @Nullable String name,
        String platform,
        List<Performance<?>> performances
) implements Playable {

    public Concert {
        if (performances == null || performances.isEmpty()) {
            throw new IllegalArgumentException("Concert must have at least one performance");
        }
        // Validate all performances are from the same platform
        for (var perf : performances) {
            if (!platform.equals(perf.source())) {
                throw new IllegalArgumentException(
                        "All performances must be from platform '%s', but found '%s'"
                                .formatted(platform, perf.source()));
            }
        }
    }

    /**
     * Create a concert with auto-generated ID, inferring platform from performances.
     */
    public Concert(@Nullable String name, List<Performance<?>> performances) {
        this(UUID.randomUUID().toString(), name, performances.getFirst().source(), performances);
    }

    /**
     * Create a concert from performances with auto-generated name.
     */
    public static Concert of(List<Performance<?>> performances) {
        return new Concert(null, performances);
    }

    /**
     * Create a named concert.
     */
    public static Concert named(String name, List<Performance<?>> performances) {
        return new Concert(name, performances);
    }

    @Override
    @NonNull
    public String getId() {
        return id;
    }

    @Override
    @NonNull
    public Instant getTimestamp() {
        return performances.getFirst().getTimestamp();
    }

    /**
     * Display title - uses name if provided, otherwise generates from works.
     */
    @Override
    public String title() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (performances.size() == 1) {
            return performances.getFirst().title();
        }
        var firstWork = performances.getFirst().workName();
        if (firstWork != null) {
            return firstWork + " and " + (performances.size() - 1) + " more";
        }
        return performances.size() + " Performances";
    }

    /**
     * Total duration is sum of all performance durations.
     */
    @Override
    public int durationSeconds() {
        return performances.stream()
                .mapToInt(Performance::durationSeconds)
                .sum();
    }

    /**
     * URL to the first performance (primary entry point).
     */
    @Override
    public String url() {
        return performances.getFirst().url();
    }

    @Override
    public String source() {
        return platform;
    }

    /**
     * Number of performances in the concert.
     */
    public int size() {
        return performances.size();
    }

    /**
     * List of unique performers across all performances.
     */
    public List<String> performers() {
        return performances.stream()
                .map(Performance::performer)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * List of unique ensembles/orchestras across all performances.
     */
    public List<String> ensembles() {
        return performances.stream()
                .map(Performance::ensemble)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * List of unique conductors across all performances.
     */
    public List<String> conductors() {
        return performances.stream()
                .map(Performance::conductor)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * List of work names in program order.
     */
    public List<String> works() {
        return performances.stream()
                .map(Performance::workName)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Tool playTool() {
        return Tool.create(
                "playConcert",
                "Play the entire concert: " + title(),
                input -> Tool.Result.text(playbackInfo())
        );
    }

    @Override
    public String playbackInfo() {
        var performanceInfos = performances.stream()
                .map(Playable::playbackInfo)
                .collect(Collectors.joining(","));
        return """
                {"type":"concert","id":"%s","title":"%s","platform":"%s","durationSeconds":%d,"performances":[%s]}
                """.formatted(id, title(), platform, durationSeconds(), performanceInfos).trim();
    }

    @Override
    @NonNull
    public LlmReference reference() {
        var duration = durationFormatted();
        var durationPart = duration != null ? duration : (size() + " works");
        // Description shows duration and platform (title is shown as the name)
        var description = String.format("[%s on %s]", durationPart, platform);

        var tools = new ArrayList<Tool>();
        tools.add(playTool());

        for (int i = 0; i < performances.size(); i++) {
            var perf = performances.get(i);
            int index = i + 1;
            tools.add(Tool.create(
                    "play_" + index,
                    "Play #" + index + ": " + perf.title(),
                    input -> Tool.Result.text(perf.playbackInfo())
            ));
        }

        var context = new StringBuilder();
        context.append("Concert: ").append(title()).append("\n");
        context.append("Platform: ").append(platform).append("\n\n");
        for (int i = 0; i < performances.size(); i++) {
            var perf = performances.get(i);
            context.append(String.format("%d. %s", i + 1, perf.title()));
            if (perf.performer() != null) {
                context.append(" - ").append(perf.performer());
            }
            var perfDuration = perf.durationFormatted();
            if (perfDuration != null) {
                context.append(" (").append(perfDuration).append(")");
            }
            context.append("\n");
        }

        // Use the actual title as the name for display
        return LlmReference.of(
                title(),
                description,
                tools,
                context.toString()
        );
    }
}
