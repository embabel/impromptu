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
package com.embabel.impromptu.integrations.imslp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the IMSLP (International Music Score Library Project) API.
 * <p>
 * IMSLP uses MediaWiki API endpoints. The workflow to find scores is:
 * <ol>
 *   <li>Search for a work page using MediaWiki search API</li>
 *   <li>Get the page content to find embedded file references</li>
 *   <li>Get file/image info to get actual PDF URLs</li>
 * </ol>
 * <p>
 * Important quirks:
 * <ul>
 *   <li>Cookie required: Must send imslpdisclaimeraccepted=yes for PDF downloads</li>
 *   <li>URL scheme: API returns URLs without https: prefix (just //imslp.org/...)</li>
 *   <li>Work naming: Pages follow pattern Work_Title_(Composer,_First_Last)</li>
 * </ul>
 *
 * @see <a href="https://imslp.org/wiki/IMSLP:API">IMSLP API Documentation</a>
 */
public class ImslpService {

    private static final Logger logger = LoggerFactory.getLogger(ImslpService.class);
    private static final String API_BASE = "https://imslp.org/api.php";
    private static final String WIKI_BASE = "https://imslp.org/wiki/";
    private static final int TIMEOUT_MS = 15_000;

    private final RestClient restClient;

    public ImslpService() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "Impromptu/1.0 (Classical Music Assistant)")
                .build();
    }

    /**
     * Search for musical works on IMSLP.
     *
     * @param query Search query (e.g., "Beethoven Symphony No.5")
     * @param limit Maximum number of results
     * @return List of search results with page titles
     */
    @SuppressWarnings("unchecked")
    public List<SearchResult> searchWorks(String query, int limit) {
        try {
            String url = API_BASE + "?action=query&list=search&srsearch=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&srlimit=" + limit +
                    "&format=json";

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.get("query") == null) {
                return List.of();
            }

            Map<String, Object> queryResult = (Map<String, Object>) response.get("query");
            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) queryResult.get("search");

            if (searchResults == null) {
                return List.of();
            }

            List<SearchResult> results = new ArrayList<>();
            for (var result : searchResults) {
                String title = (String) result.get("title");
                String snippet = (String) result.get("snippet");
                // Clean HTML from snippet
                if (snippet != null) {
                    snippet = snippet.replaceAll("<[^>]*>", "").trim();
                }
                results.add(new SearchResult(title, snippet, WIKI_BASE + encodePageTitle(title)));
            }

            logger.debug("IMSLP search for '{}' returned {} results", query, results.size());
            return results;
        } catch (Exception e) {
            logger.error("IMSLP search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get files (PDFs, images) associated with a work page.
     *
     * @param pageTitle The wiki page title (e.g., "Symphony_No.5_(Beethoven,_Ludwig_van)")
     * @return List of file names on the page
     */
    @SuppressWarnings("unchecked")
    public List<String> getPageFiles(String pageTitle) {
        try {
            String url = API_BASE + "?action=query&titles=" +
                    URLEncoder.encode(pageTitle, StandardCharsets.UTF_8) +
                    "&prop=images&imlimit=50&format=json";

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.get("query") == null) {
                return List.of();
            }

            Map<String, Object> queryResult = (Map<String, Object>) response.get("query");
            Map<String, Object> pages = (Map<String, Object>) queryResult.get("pages");

            if (pages == null) {
                return List.of();
            }

            List<String> files = new ArrayList<>();
            for (var pageEntry : pages.values()) {
                if (pageEntry instanceof Map<?, ?> page) {
                    List<Map<String, Object>> images = (List<Map<String, Object>>) page.get("images");
                    if (images != null) {
                        for (var image : images) {
                            String title = (String) image.get("title");
                            if (title != null) {
                                files.add(title);
                            }
                        }
                    }
                }
            }

            logger.debug("Found {} files for page '{}'", files.size(), pageTitle);
            return files;
        } catch (Exception e) {
            logger.error("Failed to get files for page '{}': {}", pageTitle, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get the download URL for a file.
     *
     * @param fileTitle The file title including "File:" prefix
     * @return File info with URL, or null if not found
     */
    @SuppressWarnings("unchecked")
    public FileInfo getFileInfo(String fileTitle) {
        try {
            String url = API_BASE + "?action=query&titles=" +
                    URLEncoder.encode(fileTitle, StandardCharsets.UTF_8) +
                    "&prop=imageinfo&iiprop=url|size|mime&format=json";

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.get("query") == null) {
                return null;
            }

            Map<String, Object> queryResult = (Map<String, Object>) response.get("query");
            Map<String, Object> pages = (Map<String, Object>) queryResult.get("pages");

            if (pages == null) {
                return null;
            }

            for (var pageEntry : pages.values()) {
                if (pageEntry instanceof Map<?, ?> page) {
                    List<Map<String, Object>> imageInfo = (List<Map<String, Object>>) page.get("imageinfo");
                    if (imageInfo != null && !imageInfo.isEmpty()) {
                        var info = imageInfo.get(0);
                        String fileUrl = (String) info.get("url");
                        // Fix URL scheme if missing
                        if (fileUrl != null && fileUrl.startsWith("//")) {
                            fileUrl = "https:" + fileUrl;
                        }
                        String mime = (String) info.get("mime");
                        Number size = (Number) info.get("size");

                        return new FileInfo(
                                fileTitle.replace("File:", ""),
                                fileUrl,
                                mime,
                                size != null ? size.longValue() : 0
                        );
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Failed to get file info for '{}': {}", fileTitle, e.getMessage());
            return null;
        }
    }

    /**
     * Find scores for a specific work by composer and title.
     * This performs the multi-step lookup: search → get files → get URLs
     *
     * @param composer Composer name (e.g., "Beethoven" or "Beethoven, Ludwig van")
     * @param workTitle Work title (e.g., "Symphony No.5")
     * @return Score result with page URL and PDF links
     */
    public ScoreResult findScore(String composer, String workTitle) {
        // Step 1: Search for the work
        String query = workTitle + " " + composer;
        List<SearchResult> searchResults = searchWorks(query, 10);

        if (searchResults.isEmpty()) {
            logger.info("No IMSLP results for composer='{}', title='{}'", composer, workTitle);
            return new ScoreResult(composer, workTitle, null, List.of());
        }

        // Find best matching result (prefer exact matches)
        SearchResult bestMatch = findBestMatch(searchResults, composer, workTitle);
        String pageTitle = bestMatch.pageTitle();

        logger.info("Best match for '{}' by '{}': {}", workTitle, composer, pageTitle);

        // Step 2: Get files from the page
        List<String> files = getPageFiles(pageTitle);

        // Step 3: Get URLs for PDF files
        List<PdfScore> pdfScores = new ArrayList<>();
        for (String file : files) {
            if (file.toLowerCase().endsWith(".pdf")) {
                FileInfo fileInfo = getFileInfo(file);
                if (fileInfo != null && fileInfo.url() != null) {
                    pdfScores.add(new PdfScore(
                            fileInfo.filename(),
                            fileInfo.url(),
                            describeScore(fileInfo.filename()),
                            fileInfo.size()
                    ));
                }
                // Limit to prevent too many API calls
                if (pdfScores.size() >= 10) {
                    break;
                }
            }
        }

        return new ScoreResult(
                composer,
                workTitle,
                WIKI_BASE + encodePageTitle(pageTitle),
                pdfScores
        );
    }

    /**
     * Find the best matching search result for composer and work title.
     */
    private SearchResult findBestMatch(List<SearchResult> results, String composer, String workTitle) {
        String composerLower = composer.toLowerCase();
        String titleLower = workTitle.toLowerCase();

        // Score each result
        SearchResult best = results.get(0);
        int bestScore = 0;

        for (SearchResult result : results) {
            String pageLower = result.pageTitle().toLowerCase();
            int score = 0;

            // Check for composer name match
            if (pageLower.contains(composerLower) || pageLower.contains(normalizeComposer(composer).toLowerCase())) {
                score += 50;
            }

            // Check for title match
            if (pageLower.contains(titleLower)) {
                score += 50;
            }

            // Prefer pages that look like work pages (have parentheses with composer)
            if (result.pageTitle().matches(".*\\([^)]+\\)$")) {
                score += 10;
            }

            if (score > bestScore) {
                bestScore = score;
                best = result;
            }
        }

        return best;
    }

    /**
     * Normalize composer name to IMSLP format (Last, First).
     */
    private String normalizeComposer(String composer) {
        // If already in "Last, First" format
        if (composer.contains(",")) {
            return composer.trim();
        }
        // Convert "Ludwig van Beethoven" to "Beethoven"
        String[] parts = composer.trim().split("\\s+");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        return composer;
    }

    /**
     * Generate a description for a score file based on filename.
     */
    private String describeScore(String filename) {
        String lower = filename.toLowerCase();
        List<String> parts = new ArrayList<>();

        if (lower.contains("urtext")) parts.add("Urtext");
        if (lower.contains("complete") || lower.contains("full")) parts.add("Complete");
        if (lower.contains("piano")) parts.add("Piano");
        if (lower.contains("violin")) parts.add("Violin");
        if (lower.contains("cello")) parts.add("Cello");
        if (lower.contains("orchestra")) parts.add("Orchestra");
        if (lower.contains("vocal")) parts.add("Vocal");
        if (lower.contains("score")) parts.add("Score");
        if (lower.contains("parts")) parts.add("Parts");
        if (lower.contains("arrangement") || lower.contains("arr")) parts.add("Arrangement");

        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /**
     * Encode page title for URL.
     */
    private String encodePageTitle(String title) {
        return URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);
    }

    // ========== Record types ==========

    public record SearchResult(
            String pageTitle,
            String snippet,
            String pageUrl
    ) {}

    public record FileInfo(
            String filename,
            String url,
            String mimeType,
            long size
    ) {}

    public record PdfScore(
            String filename,
            String url,
            String description,
            long size
    ) {
        public String displaySize() {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            }
        }
    }

    public record ScoreResult(
            String composer,
            String workTitle,
            String workPageUrl,
            List<PdfScore> pdfScores
    ) {
        public boolean hasScores() {
            return pdfScores != null && !pdfScores.isEmpty();
        }
    }
}
