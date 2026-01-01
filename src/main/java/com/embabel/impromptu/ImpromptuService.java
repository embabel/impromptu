package com.embabel.impromptu;

import com.embabel.agent.rag.ingestion.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.model.ContentElement;
import com.embabel.agent.rag.model.Section;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Service for ingesting and managing RAG content.
 */
@Service
public record ImpromptuService(LuceneSearchOperations luceneSearchOperations) {

    private static final String DEFAULT_LOCATION = "./data/schumann/musicandmusician001815mbp.md";
    private static final String DEFAULT_DIRECTORY = "./data";

    public String ingest(String location) {
        if (location == null || location.isBlank()) {
            location = DEFAULT_LOCATION;
        }
        if (!location.startsWith("http://") && !location.startsWith("https://")) {
            var path = Path.of(location).toAbsolutePath();
            if (path.toFile().isDirectory()) {
                return "Error: '" + location + "' is a directory. Use ingestDirectory for directories.";
            }
        }
        var uri = location.startsWith("http://") || location.startsWith("https://")
                ? location
                : Path.of(location).toAbsolutePath().toUri().toString();
        var ingested = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                .ingestUriIfNeeded(
                        luceneSearchOperations,
                        new TikaHierarchicalContentReader(),
                        uri
                );
        return ingested != null ?
                "Ingested document with ID: " + ingested :
                "Document already exists, no ingestion performed.";
    }

    public String ingestDirectory(String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            directoryPath = DEFAULT_DIRECTORY;
        }
        var dirFile = Path.of(directoryPath);
        var dir = dirFile.toAbsolutePath().toFile();

        if (dir.isFile()) {
            return "Error: '" + directoryPath + "' is a file. Use ingest for individual files.";
        }
        if (!dir.exists()) {
            return "Error: '" + directoryPath + "' does not exist.";
        }

        var dirUri = dirFile.toAbsolutePath().toUri().toString();
        var ingestedCount = 0;

        try {
            if (dir.isDirectory()) {
                var files = dir.listFiles();
                if (files != null) {
                    for (var file : files) {
                        if (file.isFile()) {
                            var fileUri = file.toPath().toAbsolutePath().toUri().toString();
                            var ingested = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                                    .ingestUriIfNeeded(
                                            luceneSearchOperations,
                                            new TikaHierarchicalContentReader(),
                                            fileUri
                                    );
                            if (ingested != null) {
                                ingestedCount++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return "Error during ingestion: " + e.getMessage();
        }

        return "Ingested " + ingestedCount + " documents from directory: " + dirUri;
    }

    public int clear() {
        return luceneSearchOperations.clear();
    }

    public String getInfo() {
        var info = luceneSearchOperations.info();
        return "Chunks: " + info.getChunkCount() + ", Documents: " + info.getDocumentCount();
    }
}
