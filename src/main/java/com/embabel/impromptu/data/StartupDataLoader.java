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

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.ingestion.policy.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.data.openopus.OpenOpusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Loads configured data sources in the background after application startup.
 * <p>
 * Configure via application.yml:
 * <pre>
 * impromptu:
 *   data-loading:
 *     open-opus: true
 *     documents:
 *       - ./data/schumann/musicandmusician001815mbp.md
 *       - https://www.gutenberg.org/files/56208/56208-h/56208-h.htm
 * </pre>
 */
@Component
public class StartupDataLoader {

    private static final Logger logger = LoggerFactory.getLogger(StartupDataLoader.class);

    private final ImpromptuProperties properties;
    private final OpenOpusService openOpusService;
    private final DrivineStore store;
    private final TikaHierarchicalContentReader contentReader = new TikaHierarchicalContentReader();

    public StartupDataLoader(
            ImpromptuProperties properties,
            OpenOpusService openOpusService,
            DrivineStore store
    ) {
        this.properties = properties;
        this.openOpusService = openOpusService;
        this.store = store;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void loadDataInBackground() {
        var dataLoading = properties.dataLoading();
        if (dataLoading == null || (!dataLoading.openOpus() && !dataLoading.hasDocuments())) {
            return;
        }

        logger.info("Starting background data loading...");

        int loaded = 0;
        int skipped = 0;

        if (dataLoading.openOpus()) {
            if (openOpusService.hasData()) {
                skipped++;
            } else {
                openOpusService.load(msg -> {});
                logger.info("Loaded Open Opus ({} composers)", 220);
                loaded++;
            }
        }

        for (String location : dataLoading.documents()) {
            String uri = resolveUri(location);
            var ingested = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                    .ingestUriIfNeeded(store, contentReader, uri);
            if (ingested != null) {
                logger.info("Loaded document: {}", ingested.getTitle());
                loaded++;
            } else {
                skipped++;
            }
        }

        logger.info("Data loading complete: {} loaded, {} skipped", loaded, skipped);
    }

    private String resolveUri(String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        return Path.of(location).toAbsolutePath().toUri().toString();
    }
}
