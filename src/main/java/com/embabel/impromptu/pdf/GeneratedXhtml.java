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
package com.embabel.impromptu.pdf;

import org.springframework.lang.Nullable;

/**
 * Result of XHTML generation, which may or may not be valid.
 *
 * @param xhtml           the generated XHTML content
 * @param valid           whether the XHTML passed validation
 * @param validationError error message if validation failed
 * @param fixAttempts     number of fix attempts made
 */
public record GeneratedXhtml(
        String xhtml,
        boolean valid,
        @Nullable String validationError,
        int fixAttempts
) {
    /**
     * Create a new GeneratedXhtml with initial (unvalidated) state.
     */
    public static GeneratedXhtml initial(String xhtml) {
        return new GeneratedXhtml(xhtml, false, null, 0);
    }

    /**
     * Create a validated version of this XHTML.
     */
    public GeneratedXhtml validated() {
        return new GeneratedXhtml(xhtml, true, null, fixAttempts);
    }

    /**
     * Create an invalid version with error message.
     */
    public GeneratedXhtml withError(String error) {
        return new GeneratedXhtml(xhtml, false, error, fixAttempts);
    }

    /**
     * Create a version with updated XHTML and incremented fix attempt.
     */
    public GeneratedXhtml withFixedXhtml(String fixedXhtml) {
        return new GeneratedXhtml(fixedXhtml, false, null, fixAttempts + 1);
    }
}
