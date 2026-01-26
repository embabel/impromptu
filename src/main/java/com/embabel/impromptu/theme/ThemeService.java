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
package com.embabel.impromptu.theme;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service providing available UI themes.
 * Themes are discovered from CSS files in the themes/ resource directory.
 *
 * Each theme CSS file should have metadata comments at the top:
 * <pre>
 * /*
 *  * Theme: theme-name
 *  * Display Name: Human Readable Name
 *  * Description: A description of the theme
 *  * Default: true/false
 *  *&#47;
 * </pre>
 */
@Service
public class ThemeService {

    private static final Logger logger = LoggerFactory.getLogger(ThemeService.class);
    private static final String THEMES_PATTERN = "classpath:themes/*.css";

    private static final Pattern THEME_PATTERN = Pattern.compile("\\*\\s*Theme:\\s*(.+)");
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("\\*\\s*Display Name:\\s*(.+)");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("\\*\\s*Description:\\s*(.+)");
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("\\*\\s*Default:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    private List<ThemeInfo> themes = new ArrayList<>();

    /**
     * Information about a UI theme.
     */
    public record ThemeInfo(
            String name,
            String displayName,
            String description,
            boolean isDefault
    ) {}

    @PostConstruct
    public void discoverThemes() {
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(THEMES_PATTERN);
            logger.info("Discovering themes from {} CSS files", resources.length);

            for (Resource resource : resources) {
                try {
                    var themeInfo = parseThemeMetadata(resource);
                    if (themeInfo != null) {
                        themes.add(themeInfo);
                        logger.info("Discovered theme: {} ({})", themeInfo.name(), themeInfo.displayName());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse theme from {}: {}", resource.getFilename(), e.getMessage());
                }
            }

            // Sort: default theme first, then alphabetically
            themes.sort(Comparator
                    .comparing(ThemeInfo::isDefault).reversed()
                    .thenComparing(ThemeInfo::displayName));

            if (themes.isEmpty()) {
                // Fallback to gold theme if no themes discovered
                logger.warn("No themes discovered, using fallback gold theme");
                themes.add(new ThemeInfo("gold", "Gold", "Classic concert hall elegance", true));
            }

        } catch (Exception e) {
            logger.error("Failed to discover themes", e);
            // Fallback
            themes.add(new ThemeInfo("gold", "Gold", "Classic concert hall elegance", true));
        }
    }

    private ThemeInfo parseThemeMetadata(Resource resource) throws Exception {
        String name = null;
        String displayName = null;
        String description = null;
        boolean isDefault = false;

        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            // Only read first 20 lines for metadata
            while ((line = reader.readLine()) != null && lineCount < 20) {
                lineCount++;

                var themeMatcher = THEME_PATTERN.matcher(line);
                if (themeMatcher.find()) {
                    name = themeMatcher.group(1).trim();
                    continue;
                }

                var displayNameMatcher = DISPLAY_NAME_PATTERN.matcher(line);
                if (displayNameMatcher.find()) {
                    displayName = displayNameMatcher.group(1).trim();
                    continue;
                }

                var descMatcher = DESCRIPTION_PATTERN.matcher(line);
                if (descMatcher.find()) {
                    description = descMatcher.group(1).trim();
                    continue;
                }

                var defaultMatcher = DEFAULT_PATTERN.matcher(line);
                if (defaultMatcher.find()) {
                    isDefault = "true".equalsIgnoreCase(defaultMatcher.group(1).trim());
                }
            }
        }

        if (name == null) {
            // Try to derive name from filename
            var filename = resource.getFilename();
            if (filename != null && filename.endsWith(".css")) {
                name = filename.substring(0, filename.length() - 4);
            }
        }

        if (name == null) {
            return null;
        }

        return new ThemeInfo(
                name,
                displayName != null ? displayName : capitalize(name),
                description != null ? description : "A visual theme",
                isDefault
        );
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Get all available themes.
     */
    public List<ThemeInfo> getAvailableThemes() {
        return themes;
    }

    /**
     * Get the default theme.
     */
    public ThemeInfo getDefaultTheme() {
        return themes.stream()
                .filter(ThemeInfo::isDefault)
                .findFirst()
                .orElse(themes.isEmpty() ? null : themes.get(0));
    }

    /**
     * Get theme CSS content by name.
     */
    public String getThemeCss(String themeName) {
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource resource = resolver.getResource("classpath:themes/" + themeName + ".css");
            if (resource.exists()) {
                try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    var sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load theme CSS for {}: {}", themeName, e.getMessage());
        }
        return null;
    }

    /**
     * Get theme CSS variables as a map for direct JavaScript property setting.
     */
    public java.util.Map<String, String> getThemeVariables(String themeName) {
        String css = getThemeCss(themeName);
        if (css == null) {
            return null;
        }

        var variables = new java.util.LinkedHashMap<String, String>();
        var pattern = Pattern.compile("--([\\w-]+)\\s*:\\s*([^;]+);");
        var matcher = pattern.matcher(css);

        while (matcher.find()) {
            String varName = "--" + matcher.group(1).trim();
            String varValue = matcher.group(2).trim();
            variables.put(varName, varValue);
        }

        logger.debug("Parsed {} CSS variables from theme {}", variables.size(), themeName);
        return variables;
    }
}
