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

import com.embabel.agent.rag.ingestion.HierarchicalContentReader;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.model.ContentRoot;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import kotlin.collections.CollectionsKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Service for managing document ingestion and retrieval for the Knowledge tab.
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final DrivineStore store;
    private final HierarchicalContentReader contentReader;

    /**
     * Summary info about an ingested document.
     */
    public record DocumentInfo(
            String uri,
            String title,
            Instant ingestedAt,
            int chunkCount
    ) {
    }

    public DocumentService(DrivineStore store) {
        this.store = store;
        this.contentReader = new TikaHierarchicalContentReader();
    }

    /**
     * Ingest content from an input stream.
     */
    public void ingestStream(InputStream inputStream, String uri, String filename) {
        logger.info("Ingesting stream: {}", filename);
        var document = contentReader.parseContent(inputStream, uri);
        store.writeAndChunkDocument(document);
        logger.info("Ingested: {}", filename);
    }

    /**
     * Ingest content from a URL.
     */
    public void ingestUrl(String url) {
        logger.debug("Ingesting URL: {}", url);
        var document = contentReader.parseResource(url);
        store.writeAndChunkDocument(document);
        logger.debug("Ingested URL: {}", url);
    }

    /**
     * Ingest HTML content directly.
     */
    public void ingestHtml(String html, String title) {
        var uri = "html://" + System.currentTimeMillis() + "/" + sanitizeTitle(title);
        logger.info("Ingesting HTML: {}", title);
        var inputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
        var document = contentReader.parseContent(inputStream, uri);
        store.writeAndChunkDocument(document);
        logger.info("Ingested HTML: {}", title);
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "untitled";
        }
        return title.replaceAll("[^a-zA-Z0-9.-]", "_").toLowerCase();
    }

    /**
     * Get list of all ingested documents from the database.
     */
    public List<DocumentInfo> getDocuments() {
        return CollectionsKt.map(store.findAll(ContentRoot.class), root ->
                new DocumentInfo(
                        root.getUri(),
                        root.getTitle(),
                        root.getIngestionTimestamp(),
                        0
                ));
    }

    /**
     * Delete a document by its URI.
     */
    public boolean deleteDocument(String uri) {
        logger.info("Deleting document: {}", uri);
        var result = store.deleteRootAndDescendants(uri);
        return result != null;
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
