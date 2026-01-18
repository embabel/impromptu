package com.embabel.impromptu.integrations.metmuseum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the Metropolitan Museum of Art Collection API.
 * <p>
 * The Met API is public and does not require authentication.
 * Limit requests to 80 per second.
 *
 * @see <a href="https://metmuseum.github.io/">Met Museum API Documentation</a>
 */
public class MetMuseumService {

    private static final Logger logger = LoggerFactory.getLogger(MetMuseumService.class);
    private static final String API_BASE = "https://collectionapi.metmuseum.org/public/collection/v1";
    private static final int TIMEOUT_MS = 10_000;

    private final RestClient restClient;

    public MetMuseumService() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Search for objects in the collection.
     *
     * @param query        Search term (required)
     * @param hasImages    Filter to objects with images
     * @param isHighlight  Filter to highlighted works
     * @param isOnView     Filter to objects currently on display
     * @param departmentId Filter by department ID
     * @return Search results with object IDs
     */
    @SuppressWarnings("unchecked")
    public SearchResult search(String query, Boolean hasImages, Boolean isHighlight,
                               Boolean isOnView, Integer departmentId) {
        var urlBuilder = new StringBuilder(API_BASE + "/search?q=")
                .append(URLEncoder.encode(query, StandardCharsets.UTF_8));

        if (hasImages != null && hasImages) {
            urlBuilder.append("&hasImages=true");
        }
        if (isHighlight != null && isHighlight) {
            urlBuilder.append("&isHighlight=true");
        }
        if (isOnView != null && isOnView) {
            urlBuilder.append("&isOnView=true");
        }
        if (departmentId != null) {
            urlBuilder.append("&departmentId=").append(departmentId);
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri(urlBuilder.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null) {
                return new SearchResult(0, List.of());
            }

            int total = response.get("total") instanceof Integer t ? t : 0;
            List<Integer> objectIDs = response.get("objectIDs") != null
                    ? (List<Integer>) response.get("objectIDs")
                    : List.of();

            logger.debug("Met Museum search for '{}' returned {} results", query, total);
            return new SearchResult(total, objectIDs);
        } catch (Exception e) {
            logger.error("Met Museum search failed for '{}': {}", query, e.getMessage());
            return new SearchResult(0, List.of());
        }
    }

    /**
     * Get detailed information about a specific object.
     *
     * @param objectId The unique object ID
     * @return Object details or null if not found
     */
    @SuppressWarnings("unchecked")
    public MetObject getObject(int objectId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(API_BASE + "/objects/" + objectId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null) {
                return null;
            }

            // Extract image URLs
            String primaryImage = (String) response.get("primaryImage");
            String primaryImageSmall = (String) response.get("primaryImageSmall");
            List<String> additionalImages = response.get("additionalImages") != null
                    ? (List<String>) response.get("additionalImages")
                    : List.of();

            // Extract tags
            List<String> tags = List.of();
            if (response.get("tags") instanceof List<?> tagsList) {
                tags = tagsList.stream()
                        .filter(t -> t instanceof Map)
                        .map(t -> (String) ((Map<?, ?>) t).get("term"))
                        .filter(t -> t != null)
                        .toList();
            }

            return new MetObject(
                    objectId,
                    (String) response.get("title"),
                    (String) response.get("artistDisplayName"),
                    (String) response.get("artistDisplayBio"),
                    (String) response.get("artistNationality"),
                    (String) response.get("objectDate"),
                    (String) response.get("medium"),
                    (String) response.get("dimensions"),
                    (String) response.get("department"),
                    (String) response.get("objectName"),
                    (String) response.get("culture"),
                    (String) response.get("period"),
                    (String) response.get("dynasty"),
                    (String) response.get("reign"),
                    (String) response.get("creditLine"),
                    (String) response.get("geographyType"),
                    (String) response.get("city"),
                    (String) response.get("country"),
                    (String) response.get("classification"),
                    Boolean.TRUE.equals(response.get("isHighlight")),
                    Boolean.TRUE.equals(response.get("isPublicDomain")),
                    primaryImage,
                    primaryImageSmall,
                    additionalImages,
                    (String) response.get("objectURL"),
                    (String) response.get("objectWikidata_URL"),
                    tags
            );
        } catch (Exception e) {
            logger.error("Failed to get Met object {}: {}", objectId, e.getMessage());
            return null;
        }
    }

    /**
     * Get all departments in the museum.
     */
    @SuppressWarnings("unchecked")
    public List<Department> getDepartments() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(API_BASE + "/departments")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.get("departments") == null) {
                return List.of();
            }

            List<Map<String, Object>> depts = (List<Map<String, Object>>) response.get("departments");
            return depts.stream()
                    .map(d -> new Department(
                            d.get("departmentId") instanceof Integer id ? id : 0,
                            (String) d.get("displayName")
                    ))
                    .toList();
        } catch (Exception e) {
            logger.error("Failed to get Met departments: {}", e.getMessage());
            return List.of();
        }
    }

    // ========== Record types ==========

    public record SearchResult(int total, List<Integer> objectIDs) {
        public List<Integer> topResults(int limit) {
            return objectIDs.size() > limit ? objectIDs.subList(0, limit) : objectIDs;
        }
    }

    public record Department(int departmentId, String displayName) {
    }

    public record MetObject(
            int objectId,
            String title,
            String artistDisplayName,
            String artistDisplayBio,
            String artistNationality,
            String objectDate,
            String medium,
            String dimensions,
            String department,
            String objectName,
            String culture,
            String period,
            String dynasty,
            String reign,
            String creditLine,
            String geographyType,
            String city,
            String country,
            String classification,
            boolean isHighlight,
            boolean isPublicDomain,
            String primaryImage,
            String primaryImageSmall,
            List<String> additionalImages,
            String objectURL,
            String wikidataURL,
            List<String> tags
    ) {
        public boolean hasImage() {
            return primaryImage != null && !primaryImage.isEmpty();
        }

        public String artistSummary() {
            if (artistDisplayName == null || artistDisplayName.isEmpty()) {
                return culture != null ? culture : "Unknown";
            }
            if (artistNationality != null && !artistNationality.isEmpty()) {
                return artistDisplayName + " (" + artistNationality + ")";
            }
            return artistDisplayName;
        }

        public String displaySummary() {
            var sb = new StringBuilder();
            sb.append("**").append(title != null ? title : "Untitled").append("**");

            if (artistDisplayName != null && !artistDisplayName.isEmpty()) {
                sb.append(" by ").append(artistDisplayName);
            }

            if (objectDate != null && !objectDate.isEmpty()) {
                sb.append(" (").append(objectDate).append(")");
            }

            if (medium != null && !medium.isEmpty()) {
                sb.append("\n").append(medium);
            }

            if (department != null && !department.isEmpty()) {
                sb.append("\nDepartment: ").append(department);
            }

            return sb.toString();
        }
    }
}
