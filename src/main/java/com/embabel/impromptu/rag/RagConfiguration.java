/*
 * Copyright 2024-2026 Embabel Pty Ltd.
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

import com.embabel.agent.rag.ingestion.ChunkTransformer;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import com.embabel.agent.rag.neo.drivine.DrivineCypherSearch;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import com.embabel.impromptu.ImpromptuProperties;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.GraphObjectManagerFactory;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(ImpromptuProperties.class)
class RagConfiguration {

    @Bean
    PersistenceManager persistenceManager(PersistenceManagerFactory factory) {
        return factory.get("neo");
    }

    @Bean
    GraphObjectManager graphObjectManager(GraphObjectManagerFactory factory) {
        return factory.get("neo");
    }

    @Bean
    @Primary
    EmbeddingService embeddingService(
            ModelProvider modelProvider,
            ImpromptuProperties properties) {
        return modelProvider.getEmbeddingService(
                ModelSelectionCriteria.byName(
                        properties.embeddingService())
        );
    }

    @Bean
    ChunkTransformer chunkTransformer() {
        return AddTitlesChunkTransformer.INSTANCE;
    }

    @Bean
    @Primary
    DrivineStore drivineStore(
            PersistenceManager persistenceManager,
            PlatformTransactionManager platformTransactionManager,
            EmbeddingService embeddingService,
            ChunkTransformer chunkTransformer,
            ImpromptuProperties properties) {
        var store = new DrivineStore(
                persistenceManager,
                properties.neoRag(),
                properties.chunkerConfig(),
                chunkTransformer,
                embeddingService,
                platformTransactionManager,
                new DrivineCypherSearch(persistenceManager)
        );
        store.provision();
        return store;
    }

}
