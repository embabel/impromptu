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
package com.embabel.impromptu.rag;

import com.embabel.agent.rag.ingestion.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.model.NavigableContainerSection;
import com.embabel.agent.rag.model.NavigableDocument;
import com.embabel.agent.rag.model.NavigableSection;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * REST endpoint for ingesting documents into the RAG store.
 * <p>
 * Usage:
 * <pre>
 * # Ingest a URL (e.g., Gutenberg HTML)
 * curl -X POST "http://localhost:8888/api/documents/ingest?location=https://www.gutenberg.org/files/56208/56208-h/56208-h.htm"
 *
 * # Ingest a local file
 * curl -X POST "http://localhost:8888/api/documents/ingest?location=./data/schumann/musicandmusician001815mbp.md"
 *
 * # Ingest a directory
 * curl -X POST "http://localhost:8888/api/documents/ingest-directory?path=./data"
 *
 * # Get store info
 * curl http://localhost:8888/api/documents/info
 * </pre>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentIngestionController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionController.class);

    private final DrivineStore store;
    private final TikaHierarchicalContentReader contentReader;

    public DocumentIngestionController(DrivineStore store) {
        this.store = store;
        this.contentReader = new TikaHierarchicalContentReader();
    }

    private void log(PrintWriter writer, String message) {
        logger.info(message);
        writer.println(message);
        writer.flush();
    }

    @PostMapping(value = "/ingest", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody ingest(@RequestParam String location) {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            try {
                log(writer, "Ingesting: " + location);

                String uri = resolveUri(location);
                log(writer, "Resolved URI: " + uri);

                var ingested = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                        .ingestUriIfNeeded(store, contentReader, uri);

                if (ingested != null) {
                    log(writer, "Ingested document: " + ingested.getId());
                    log(writer, "Title: " + ingested.getTitle());
                    log(writer, "Sections: " + countSections(ingested));
                } else {
                    log(writer, "Document already exists, skipped.");
                }

                var info = store.info();
                log(writer, "Store now has " + info.getChunkCount() + " chunks from " + info.getDocumentCount() + " documents");
                log(writer, "Done!");

            } catch (Exception e) {
                log(writer, "Error: " + e.getMessage());
                logger.error("Document ingestion failed", e);
            }
        };
    }

    @PostMapping(value = "/ingest-directory", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody ingestDirectory(@RequestParam String path) {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            try {
                File dir = Path.of(path).toAbsolutePath().toFile();
                log(writer, "Ingesting directory: " + dir.getAbsolutePath());

                if (!dir.exists()) {
                    log(writer, "Error: Directory does not exist");
                    return;
                }
                if (!dir.isDirectory()) {
                    log(writer, "Error: Path is not a directory. Use /ingest for files.");
                    return;
                }

                int ingestedCount = 0;
                int skippedCount = 0;
                File[] files = dir.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isSupported(file.getName())) {
                            String uri = file.toPath().toAbsolutePath().toUri().toString();
                            var ingested = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                                    .ingestUriIfNeeded(store, contentReader, uri);

                            if (ingested != null) {
                                log(writer, "  Ingested: " + file.getName());
                                ingestedCount++;
                            } else {
                                skippedCount++;
                            }
                        }
                    }
                }

                log(writer, "Ingested " + ingestedCount + " documents, skipped " + skippedCount + " (already exist)");

                var info = store.info();
                log(writer, "Store now has " + info.getChunkCount() + " chunks from " + info.getDocumentCount() + " documents");
                log(writer, "Done!");

            } catch (Exception e) {
                log(writer, "Error: " + e.getMessage());
                logger.error("Directory ingestion failed", e);
            }
        };
    }

    @GetMapping(value = "/info", produces = MediaType.TEXT_PLAIN_VALUE)
    public String info() {
        var info = store.info();
        return "Chunks: " + info.getChunkCount() + "\nDocuments: " + info.getDocumentCount();
    }

    private String resolveUri(String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        return Path.of(location).toAbsolutePath().toUri().toString();
    }

    private boolean isSupported(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".html")
                || lower.endsWith(".htm") || lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".doc") || lower.endsWith(".rtf") || lower.endsWith(".odt");
    }

    private int countSections(NavigableDocument doc) {
        return countChildren(doc.getChildren());
    }

    private int countChildren(Iterable<? extends NavigableSection> sections) {
        int count = 0;
        for (var section : sections) {
            count++;
            if (section instanceof NavigableContainerSection container) {
                count += countChildren(container.getChildren());
            }
        }
        return count;
    }
}
