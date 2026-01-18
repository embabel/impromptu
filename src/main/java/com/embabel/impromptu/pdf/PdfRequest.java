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
 * Request for PDF generation.
 *
 * @param purpose the type of document (e.g., "invoice", "report", "summary", "concert program")
 * @param content the structured data or text to include in the PDF
 * @param style   optional style preset to use
 */
public record PdfRequest(
        String purpose,
        String content,
        @Nullable PdfStyle style
) {
    public PdfRequest(String purpose, String content) {
        this(purpose, content, PdfStyle.PROFESSIONAL);
    }

    public PdfStyle effectiveStyle() {
        return style != null ? style : PdfStyle.PROFESSIONAL;
    }
}
