package com.embabel.impromptu.speech;

import com.embabel.impromptu.ImpromptuProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for discovering and managing available personas (voices).
 * Personas are defined as Jinja templates in the prompts/personas directory.
 */
@Service
public record PersonaService(ImpromptuProperties properties) {

    private static final String PERSONAS_PATH = "classpath:prompts/personas/*.jinja";

    /**
     * Get all available personas.
     *
     * @return list of persona info sorted by name, with default first
     */
    public List<PersonaInfo> getAvailablePersonas() {
        var personas = new ArrayList<PersonaInfo>();
        var resolver = new PathMatchingResourcePatternResolver();

        try {
            var resources = resolver.getResources(PERSONAS_PATH);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith(".jinja")) {
                    String name = filename.replace(".jinja", "");
                    String description = extractDescription(resource);
                    boolean isDefault = name.equals(properties.defaultVoice().persona());
                    personas.add(new PersonaInfo(name, description, isDefault));
                }
            }
        } catch (IOException e) {
            // Return empty list if we can't read personas
        }

        // Sort: default first, then alphabetically
        personas.sort(Comparator
                .comparing((PersonaInfo p) -> !p.isDefault())
                .thenComparing(PersonaInfo::name));

        return personas;
    }

    /**
     * Description comment prefix for persona files.
     * Example: {# description: Friendly classical music guide #}
     */
    private static final String DESCRIPTION_PREFIX = "{# description:";
    private static final String COMMENT_SUFFIX = "#}";

    /**
     * Extract description from persona file.
     * Looks for a Jinja comment on the first line: {# description: ... #}
     * Falls back to cleaning up the first line of content if no description comment found.
     */
    private String extractDescription(Resource resource) {
        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String trimmed = firstLine.trim().toLowerCase();
                // Check for description comment: {# description: ... #}
                if (trimmed.startsWith(DESCRIPTION_PREFIX.toLowerCase())) {
                    String description = firstLine.trim();
                    // Remove prefix
                    description = description.substring(DESCRIPTION_PREFIX.length()).trim();
                    // Remove suffix if present
                    if (description.endsWith(COMMENT_SUFFIX)) {
                        description = description.substring(0, description.length() - COMMENT_SUFFIX.length()).trim();
                    }
                    return description;
                }
                // Fallback: clean up the first line of content
                String description = firstLine.trim();
                if (description.toLowerCase().startsWith("you are ")) {
                    description = description.substring(8);
                }
                if (!description.isEmpty()) {
                    description = Character.toUpperCase(description.charAt(0)) + description.substring(1);
                }
                if (description.endsWith(".")) {
                    description = description.substring(0, description.length() - 1);
                }
                return description;
            }
        } catch (IOException e) {
            // Ignore and return default
        }
        return "No description available";
    }

    /**
     * Information about an available persona.
     */
    public record PersonaInfo(
            String name,
            String description,
            boolean isDefault
    ) {
        /**
         * Display name for UI, showing "(Default)" suffix if applicable.
         */
        public String displayName() {
            return isDefault ? name + " (Default)" : name;
        }
    }
}
