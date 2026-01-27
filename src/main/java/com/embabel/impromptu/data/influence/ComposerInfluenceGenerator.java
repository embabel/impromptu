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
package com.embabel.impromptu.data.influence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates composer influence relationships and writes to CSV for review.
 */
public interface ComposerInfluenceGenerator {

    /** Shared constant for the influence CSV file location. */
    Path INFLUENCES_CSV_PATH = Path.of("data/influences/composer-influences.csv");

    record ComposerInfo(String id, String name, String completeName, Long birthYear, Long deathYear) {}

    record InfluenceRecord(
            String from,
            String to,
            String reason,
            double strength,
            double confidence,
            double divergence,
            String status
    ) {
        public String toCsvLine() {
            var escapedReason = reason.replace("\"", "\"\"");
            return String.format("%s,%s,\"%s\",%.2f,%.2f,%.2f,%s",
                    from, to, escapedReason, strength, confidence, divergence, status);
        }
    }

    /**
     * Load all composers from the database.
     */
    List<ComposerInfo> loadComposers();

    /**
     * Generate influence relationships for a single composer.
     */
    List<InfluenceRecord> generateInfluencesFor(ComposerInfo composer, List<ComposerInfo> allComposers);

    /**
     * Generate influences for all composers and write to CSV.
     */
    void generateAllInfluences() throws IOException;
}
