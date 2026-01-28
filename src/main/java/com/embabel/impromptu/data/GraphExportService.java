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
package com.embabel.impromptu.data;

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports Neo4j graph data to CSV files for fast bulk import.
 * Uses LOAD CSV with PERIODIC COMMIT for efficient loading.
 */
@Service
public class GraphExportService {

    private static final Logger logger = LoggerFactory.getLogger(GraphExportService.class);
    private static final Path EXPORT_DIR = Path.of("data/exports");

    private final PersistenceManager persistenceManager;

    public GraphExportService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public record ExportResult(int nodes, int relationships, Path directory) {}

    /**
     * Export all Reference nodes and their relationships to CSV files.
     */
    public ExportResult exportReferences() throws IOException {
        Files.createDirectories(EXPORT_DIR);

        // Get counts
        Long nodeCount = persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (n:Reference) RETURN count(n) AS count")
                        .transform(Long.class)
        );
        Long relCount = persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (:Reference)-[r]->(:Reference) RETURN count(r) AS count")
                        .transform(Long.class)
        );

        logger.info("Exporting {} nodes and {} relationships to CSV", nodeCount, relCount);

        // Export nodes
        int composers = exportComposersCsv();
        int works = exportWorksCsv();
        int epochs = exportEpochsCsv();
        int genres = exportGenresCsv();

        // Export relationships
        int composedRels = exportComposedRelsCsv();
        int epochRels = exportEpochRelsCsv();
        int genreRels = exportGenreRelsCsv();

        // Generate import script
        generateImportCypher();

        logger.info("Exported to {}", EXPORT_DIR.toAbsolutePath());
        return new ExportResult(nodeCount.intValue(), relCount.intValue(), EXPORT_DIR);
    }

    private int exportComposersCsv() throws IOException {
        // Build CSV line server-side - always quote strings, double internal quotes
        var query = """
                MATCH (c:Composer)
                WITH c,
                     '"' + REPLACE(c.id, '"', '""') + '"' AS id,
                     '"' + REPLACE(c.name, '"', '""') + '"' AS name,
                     CASE WHEN c.completeName IS NULL THEN '' ELSE '"' + REPLACE(c.completeName, '"', '""') + '"' END AS completeName,
                     COALESCE(toString(c.birthYear), '') AS birthYear,
                     COALESCE(toString(c.deathYear), '') AS deathYear,
                     CASE WHEN c.description IS NULL THEN '' ELSE '"' + REPLACE(REPLACE(REPLACE(c.description, '"', '""'), '\n', ' '), '\r', '') + '"' END AS description,
                     COALESCE(toString(c.popular), 'false') AS popular,
                     COALESCE(toString(c.recommended), 'false') AS recommended,
                     COALESCE(c.primarySource, '') AS primarySource
                RETURN id + ',' + name + ',' + completeName + ',' + birthYear + ',' + deathYear + ',' + description + ',' + popular + ',' + recommended + ',' + primarySource AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("composers.csv").toFile()))) {
            writer.println("id,name,completeName,birthYear,deathYear,description,popular,recommended,primarySource");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} composers", lines.size());
        return lines.size();
    }

    private int exportWorksCsv() throws IOException {
        var query = """
                MATCH (w:Work)
                WITH w,
                     '"' + REPLACE(w.id, '"', '""') + '"' AS id,
                     '"' + REPLACE(w.name, '"', '""') + '"' AS name,
                     CASE WHEN w.title IS NULL THEN '' ELSE '"' + REPLACE(w.title, '"', '""') + '"' END AS title,
                     CASE WHEN w.description IS NULL THEN '' ELSE '"' + REPLACE(REPLACE(REPLACE(w.description, '"', '""'), '\n', ' '), '\r', '') + '"' END AS description,
                     COALESCE(toString(w.popular), 'false') AS popular,
                     COALESCE(toString(w.recommended), 'false') AS recommended,
                     COALESCE(w.primarySource, '') AS primarySource
                RETURN id + ',' + name + ',' + title + ',' + description + ',' + popular + ',' + recommended + ',' + primarySource AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("works.csv").toFile()))) {
            writer.println("id,name,title,description,popular,recommended,primarySource");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} works", lines.size());
        return lines.size();
    }

    private int exportEpochsCsv() throws IOException {
        var query = """
                MATCH (e:Epoch)
                RETURN '"' + e.id + '","' + REPLACE(e.name, '"', '""') + '",' + COALESCE(e.primarySource, '') AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("epochs.csv").toFile()))) {
            writer.println("id,name,primarySource");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} epochs", lines.size());
        return lines.size();
    }

    private int exportGenresCsv() throws IOException {
        var query = """
                MATCH (g:Genre)
                RETURN '"' + g.id + '","' + REPLACE(g.name, '"', '""') + '",' + COALESCE(g.primarySource, '') AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("genres.csv").toFile()))) {
            writer.println("id,name,primarySource");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} genres", lines.size());
        return lines.size();
    }

    private int exportComposedRelsCsv() throws IOException {
        var query = """
                MATCH (c:Composer)-[:COMPOSED]->(w:Work)
                RETURN '"' + c.id + '","' + w.id + '"' AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("composed_rels.csv").toFile()))) {
            writer.println("composerId,workId");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} COMPOSED relationships", lines.size());
        return lines.size();
    }

    private int exportEpochRelsCsv() throws IOException {
        var query = """
                MATCH (c:Composer)-[:OF_EPOCH]->(e:Epoch)
                RETURN '"' + c.id + '","' + e.id + '"' AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("epoch_rels.csv").toFile()))) {
            writer.println("composerId,epochId");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} OF_EPOCH relationships", lines.size());
        return lines.size();
    }

    private int exportGenreRelsCsv() throws IOException {
        var query = """
                MATCH (w:Work)-[:OF_GENRE]->(g:Genre)
                RETURN '"' + w.id + '","' + g.id + '"' AS line
                """;

        List<String> lines = persistenceManager.query(
                QuerySpecification.withStatement(query).transform(String.class)
        );

        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("genre_rels.csv").toFile()))) {
            writer.println("workId,genreId");
            lines.forEach(writer::println);
        }
        logger.info("Exported {} OF_GENRE relationships", lines.size());
        return lines.size();
    }

    private void generateImportCypher() throws IOException {
        try (var writer = new PrintWriter(new FileWriter(EXPORT_DIR.resolve("import_references.cypher").toFile()))) {
            writer.println("// Generated by Impromptu GraphExportService");
            writer.println("// Run: ./scripts/load_data.sh");
            writer.println();
            writer.println("// === CONSTRAINTS ===");
            writer.println("CREATE CONSTRAINT composer_id IF NOT EXISTS FOR (c:Composer) REQUIRE c.id IS UNIQUE;");
            writer.println("CREATE CONSTRAINT work_id IF NOT EXISTS FOR (w:Work) REQUIRE w.id IS UNIQUE;");
            writer.println("CREATE CONSTRAINT epoch_id IF NOT EXISTS FOR (e:Epoch) REQUIRE e.id IS UNIQUE;");
            writer.println("CREATE CONSTRAINT genre_id IF NOT EXISTS FOR (g:Genre) REQUIRE g.id IS UNIQUE;");
            writer.println();
            writer.println("// === COMPOSERS ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///composers.csv' AS row
                    CREATE (:Composer:Reference {
                      id: row.id,
                      name: row.name,
                      completeName: CASE WHEN row.completeName = '' THEN null ELSE row.completeName END,
                      birthYear: CASE WHEN row.birthYear = '' THEN null ELSE toInteger(row.birthYear) END,
                      deathYear: CASE WHEN row.deathYear = '' THEN null ELSE toInteger(row.deathYear) END,
                      description: CASE WHEN row.description = '' THEN null ELSE row.description END,
                      popular: row.popular = 'true',
                      recommended: row.recommended = 'true',
                      primarySource: CASE WHEN row.primarySource = '' THEN null ELSE row.primarySource END
                    });""");
            writer.println();
            writer.println("// === WORKS ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///works.csv' AS row
                    CREATE (:Work:Reference {
                      id: row.id,
                      name: row.name,
                      title: CASE WHEN row.title = '' THEN null ELSE row.title END,
                      description: CASE WHEN row.description = '' THEN null ELSE row.description END,
                      popular: row.popular = 'true',
                      recommended: row.recommended = 'true',
                      primarySource: CASE WHEN row.primarySource = '' THEN null ELSE row.primarySource END
                    });""");
            writer.println();
            writer.println("// === EPOCHS ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///epochs.csv' AS row
                    CREATE (:Epoch:Reference {
                      id: row.id,
                      name: row.name,
                      primarySource: CASE WHEN row.primarySource = '' THEN null ELSE row.primarySource END
                    });""");
            writer.println();
            writer.println("// === GENRES ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///genres.csv' AS row
                    CREATE (:Genre:Reference {
                      id: row.id,
                      name: row.name,
                      primarySource: CASE WHEN row.primarySource = '' THEN null ELSE row.primarySource END
                    });""");
            writer.println();
            writer.println("// === COMPOSED RELATIONSHIPS ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///composed_rels.csv' AS row
                    MATCH (c:Composer {id: row.composerId})
                    MATCH (w:Work {id: row.workId})
                    CREATE (c)-[:COMPOSED]->(w);""");
            writer.println();
            writer.println("// === OF_EPOCH RELATIONSHIPS ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///epoch_rels.csv' AS row
                    MATCH (c:Composer {id: row.composerId})
                    MATCH (e:Epoch {id: row.epochId})
                    CREATE (c)-[:OF_EPOCH]->(e);""");
            writer.println();
            writer.println("// === OF_GENRE RELATIONSHIPS ===");
            writer.println("""
                    LOAD CSV WITH HEADERS FROM 'file:///genre_rels.csv' AS row
                    MATCH (w:Work {id: row.workId})
                    MATCH (g:Genre {id: row.genreId})
                    CREATE (w)-[:OF_GENRE]->(g);""");
        }
        logger.info("Generated import_references.cypher");
    }

    /**
     * Get the export directory path.
     */
    public Path getExportPath() {
        return EXPORT_DIR;
    }
}
