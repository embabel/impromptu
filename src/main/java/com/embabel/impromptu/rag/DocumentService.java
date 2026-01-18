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

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.model.NavigableDocument;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing document ingestion and retrieval for the Knowledge tab.
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final DrivineStore store;
    private final TikaHierarchicalContentReader contentReader;
    private final List<DocumentInfo> documents = new CopyOnWriteArrayList<>();

    /**
     * Summary info about an ingested document.
     */
    public record DocumentInfo(String uri, String title, Instant ingestedAt) {
    }

    public DocumentService(DrivineStore store) {
        this.store = store;
        this.contentReader = new TikaHierarchicalContentReader();
    }

    /**
     * Ingest content from an input stream.
     */
    public NavigableDocument ingestStream(InputStream inputStream, String uri, String filename) {
        logger.info("Ingesting stream: {}", filename);
        var document = contentReader.parseContent(inputStream, uri);
        store.writeAndChunkDocument(document);
        trackDocument(document, filename);
        logger.info("Ingested: {}", filename);
        return document;
    }

    /**
     * Ingest content from a URL.
     */
    public NavigableDocument ingestUrl(String url) {
        logger.info("Ingesting URL: {}", url);
        var document = contentReader.parseResource(url);
        store.writeAndChunkDocument(document);
        trackDocument(document, null);
        logger.info("Ingested URL: {}", url);
        return document;
    }

    /**
     * Ingest HTML content directly.
     */
    public NavigableDocument ingestHtml(String html, String title) {
        var uri = "html://" + System.currentTimeMillis() + "/" + sanitizeTitle(title);
        logger.info("Ingesting HTML: {}", title);
        var inputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
        var document = contentReader.parseContent(inputStream, uri);
        store.writeAndChunkDocument(document);
        trackDocument(document, title);
        logger.info("Ingested HTML: {}", title);
        return document;
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "untitled";
        }
        return title.replaceAll("[^a-zA-Z0-9.-]", "_").toLowerCase();
    }

    private void trackDocument(NavigableDocument document, String overrideTitle) {
        var title = overrideTitle != null ? overrideTitle : document.getTitle();
        documents.add(new DocumentInfo(
                document.getUri(),
                title,
                Instant.now()
        ));
    }

    /**
     * Get list of all ingested documents.
     */
    public List<DocumentInfo> getDocuments() {
        return List.copyOf(documents);
    }

    /**
     * Delete a document by its URI.
     */
    public boolean deleteDocument(String uri) {
        logger.info("Deleting document: {}", uri);
        var result = store.deleteRootAndDescendants(uri);
        if (result != null) {
            documents.removeIf(doc -> doc.uri().equals(uri));
            return true;
        }
        return false;
    }

    /**
     * Get total document count.
     */
    public int getDocumentCount() {
        return store.info().getDocumentCount();
    }

    /**
     * Get total chunk count.
     */
    public int getChunkCount() {
        return store.info().getChunkCount();
    }
}
