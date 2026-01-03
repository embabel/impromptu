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
package com.embabel.impromptu.data.openopus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for parsing work data from Open Opus JSON dump.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenOpusWork(
        @JsonProperty("title") String title,
        @JsonProperty("subtitle") String subtitle,
        @JsonProperty("searchterms") String searchTerms,
        @JsonProperty("popular") String popular,
        @JsonProperty("recommended") String recommended,
        @JsonProperty("genre") String genre
) {
    public boolean isPopular() {
        return "1".equals(popular);
    }

    public boolean isRecommended() {
        return "1".equals(recommended);
    }
}
