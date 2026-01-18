package com.embabel.impromptu.integrations.metmuseum;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM tools for Metropolitan Museum of Art Collection API integration.
 * <p>
 * Uses {@link MatryoshkaTools} for progressive tool disclosure. The LLM first sees
 * a "metmuseum" facade tool. When invoked, all Met Museum-specific tools become available.
 * <p>
 * The Met API is public and does not require authentication.
 *
 * @see <a href="https://metmuseum.github.io/">Met Museum API Documentation</a>
 */
@MatryoshkaTools(
        name = "metmuseum",
        description = "Access the Metropolitan Museum of Art collection. Invoke this tool to search artworks, " +
                "get artwork details, explore departments, and discover highlighted works from one of the world's " +
                "largest art museums."
)
public record MetMuseumTools(
        MetMuseumService metMuseumService
) {

    private static final Logger logger = LoggerFactory.getLogger(MetMuseumTools.class);

    public static final MetMuseumTools DEFAULT = new MetMuseumTools(new MetMuseumService());

    /**
     * Search for artworks in the Met collection.
     */
    @LlmTool(description = "Search for artworks in the Metropolitan Museum of Art collection by keyword. " +
            "Returns a list of matching artworks with basic information.")
    public String searchArtworks(String query, Boolean hasImages) {
        try {
            var result = metMuseumService.search(query, hasImages, null, null, null);

            if (result.total() == 0) {
                return "No artworks found for: " + query;
            }

            // Get details for top results (limit to avoid too many API calls)
            List<Integer> topIds = result.topResults(5);
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(result.total()).append(" artworks for '").append(query).append("'.\n\n");
            sb.append("Top results:\n\n");

            int shown = 0;
            for (Integer objectId : topIds) {
                var obj = metMuseumService.getObject(objectId);
                if (obj != null) {
                    sb.append(shown + 1).append(". ").append(obj.displaySummary()).append("\n");
                    if (obj.hasImage()) {
                        sb.append("   Image: ").append(obj.primaryImageSmall()).append("\n");
                    }
                    sb.append("   ID: ").append(obj.objectId()).append("\n\n");
                    shown++;
                }
            }

            if (result.total() > 5) {
                sb.append("Use getArtworkDetails with an ID to see more information about a specific artwork.");
            }

            logger.debug("Met Museum search for '{}' returned {} results, showed {}", query, result.total(), shown);
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to search Met Museum for '{}': {}", query, e.getMessage());
            return "Failed to search the Met collection: " + e.getMessage();
        }
    }

    /**
     * Search for highlighted/notable artworks.
     */
    @LlmTool(description = "Search for highlighted artworks in the Met collection - these are notable, " +
            "important works that the museum has designated as highlights of the collection.")
    public String searchHighlightedArtworks(String query) {
        try {
            var result = metMuseumService.search(query, true, true, null, null);

            if (result.total() == 0) {
                return "No highlighted artworks found for: " + query;
            }

            List<Integer> topIds = result.topResults(5);
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(result.total()).append(" highlighted artworks for '").append(query).append("'.\n\n");

            int shown = 0;
            for (Integer objectId : topIds) {
                var obj = metMuseumService.getObject(objectId);
                if (obj != null) {
                    sb.append(shown + 1).append(". ").append(obj.displaySummary()).append("\n");
                    if (obj.hasImage()) {
                        sb.append("   Image: ").append(obj.primaryImageSmall()).append("\n");
                    }
                    sb.append("   ID: ").append(obj.objectId()).append("\n\n");
                    shown++;
                }
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to search Met highlights for '{}': {}", query, e.getMessage());
            return "Failed to search highlighted artworks: " + e.getMessage();
        }
    }

    /**
     * Search for artworks currently on display.
     */
    @LlmTool(description = "Search for artworks currently on display at the Metropolitan Museum. " +
            "Useful for planning a visit or seeing what's currently viewable.")
    public String searchOnViewArtworks(String query) {
        try {
            var result = metMuseumService.search(query, true, null, true, null);

            if (result.total() == 0) {
                return "No artworks currently on display found for: " + query;
            }

            List<Integer> topIds = result.topResults(5);
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(result.total()).append(" artworks currently on view for '")
                    .append(query).append("'.\n\n");

            int shown = 0;
            for (Integer objectId : topIds) {
                var obj = metMuseumService.getObject(objectId);
                if (obj != null) {
                    sb.append(shown + 1).append(". ").append(obj.displaySummary()).append("\n");
                    if (obj.department() != null) {
                        sb.append("   Gallery: ").append(obj.department()).append("\n");
                    }
                    if (obj.hasImage()) {
                        sb.append("   Image: ").append(obj.primaryImageSmall()).append("\n");
                    }
                    sb.append("   ID: ").append(obj.objectId()).append("\n\n");
                    shown++;
                }
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to search Met on-view artworks for '{}': {}", query, e.getMessage());
            return "Failed to search on-view artworks: " + e.getMessage();
        }
    }

    /**
     * Get detailed information about a specific artwork.
     */
    @LlmTool(description = "Get detailed information about a specific artwork from the Met collection by its ID. " +
            "Returns comprehensive details including artist, date, medium, dimensions, and images.")
    public String getArtworkDetails(int objectId) {
        try {
            var obj = metMuseumService.getObject(objectId);

            if (obj == null) {
                return "Artwork with ID " + objectId + " not found.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(obj.title() != null ? obj.title() : "Untitled").append("\n\n");

            // Artist info
            if (obj.artistDisplayName() != null && !obj.artistDisplayName().isEmpty()) {
                sb.append("**Artist:** ").append(obj.artistDisplayName());
                if (obj.artistDisplayBio() != null && !obj.artistDisplayBio().isEmpty()) {
                    sb.append(" (").append(obj.artistDisplayBio()).append(")");
                }
                sb.append("\n");
            }

            // Date
            if (obj.objectDate() != null && !obj.objectDate().isEmpty()) {
                sb.append("**Date:** ").append(obj.objectDate()).append("\n");
            }

            // Medium and dimensions
            if (obj.medium() != null && !obj.medium().isEmpty()) {
                sb.append("**Medium:** ").append(obj.medium()).append("\n");
            }
            if (obj.dimensions() != null && !obj.dimensions().isEmpty()) {
                sb.append("**Dimensions:** ").append(obj.dimensions()).append("\n");
            }

            // Classification and culture
            if (obj.classification() != null && !obj.classification().isEmpty()) {
                sb.append("**Classification:** ").append(obj.classification()).append("\n");
            }
            if (obj.culture() != null && !obj.culture().isEmpty()) {
                sb.append("**Culture:** ").append(obj.culture()).append("\n");
            }
            if (obj.period() != null && !obj.period().isEmpty()) {
                sb.append("**Period:** ").append(obj.period()).append("\n");
            }

            // Location/origin
            if (obj.country() != null && !obj.country().isEmpty()) {
                sb.append("**Country:** ").append(obj.country());
                if (obj.city() != null && !obj.city().isEmpty()) {
                    sb.append(", ").append(obj.city());
                }
                sb.append("\n");
            }

            // Department
            if (obj.department() != null && !obj.department().isEmpty()) {
                sb.append("**Department:** ").append(obj.department()).append("\n");
            }

            // Credit line
            if (obj.creditLine() != null && !obj.creditLine().isEmpty()) {
                sb.append("**Credit:** ").append(obj.creditLine()).append("\n");
            }

            // Status
            sb.append("\n");
            if (obj.isHighlight()) {
                sb.append("⭐ **Highlighted work**\n");
            }
            if (obj.isPublicDomain()) {
                sb.append("🌐 **Public domain** - images can be freely used\n");
            }

            // Tags
            if (obj.tags() != null && !obj.tags().isEmpty()) {
                sb.append("\n**Tags:** ").append(String.join(", ", obj.tags())).append("\n");
            }

            // Images
            if (obj.hasImage()) {
                sb.append("\n**Primary Image:** ").append(obj.primaryImage()).append("\n");
                if (obj.additionalImages() != null && !obj.additionalImages().isEmpty()) {
                    sb.append("**Additional Images:** ").append(obj.additionalImages().size()).append(" available\n");
                }
            }

            // Links
            if (obj.objectURL() != null && !obj.objectURL().isEmpty()) {
                sb.append("\n[View on Met Museum website](").append(obj.objectURL()).append(")\n");
            }

            logger.debug("Retrieved Met object details for ID {}", objectId);
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to get Met object {}: {}", objectId, e.getMessage());
            return "Failed to get artwork details: " + e.getMessage();
        }
    }

    /**
     * List all museum departments.
     */
    @LlmTool(description = "List all departments in the Metropolitan Museum of Art. " +
            "Useful for browsing the collection by department or filtering searches.")
    public String getDepartments() {
        try {
            var departments = metMuseumService.getDepartments();

            if (departments.isEmpty()) {
                return "Could not retrieve department list.";
            }

            StringBuilder sb = new StringBuilder("**Metropolitan Museum Departments:**\n\n");
            for (var dept : departments) {
                sb.append("- ").append(dept.displayName())
                        .append(" (ID: ").append(dept.departmentId()).append(")\n");
            }

            sb.append("\nUse searchByDepartment to search within a specific department.");
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to get Met departments: {}", e.getMessage());
            return "Failed to get departments: " + e.getMessage();
        }
    }

    /**
     * Search within a specific department.
     */
    @LlmTool(description = "Search for artworks within a specific Met Museum department. " +
            "Use getDepartments to see available departments and their IDs.")
    public String searchByDepartment(String query, int departmentId) {
        try {
            var result = metMuseumService.search(query, true, null, null, departmentId);

            if (result.total() == 0) {
                return "No artworks found for '" + query + "' in department " + departmentId;
            }

            List<Integer> topIds = result.topResults(5);
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(result.total()).append(" artworks for '").append(query)
                    .append("' in department ").append(departmentId).append(".\n\n");

            int shown = 0;
            for (Integer objectId : topIds) {
                var obj = metMuseumService.getObject(objectId);
                if (obj != null) {
                    sb.append(shown + 1).append(". ").append(obj.displaySummary()).append("\n");
                    if (obj.hasImage()) {
                        sb.append("   Image: ").append(obj.primaryImageSmall()).append("\n");
                    }
                    sb.append("   ID: ").append(obj.objectId()).append("\n\n");
                    shown++;
                }
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to search Met department {} for '{}': {}", departmentId, query, e.getMessage());
            return "Failed to search department: " + e.getMessage();
        }
    }
}
