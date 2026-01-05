package com.embabel.impromptu.rag;

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
    @Primary
    DrivineStore drivineStore(
            PersistenceManager persistenceManager,
            PlatformTransactionManager platformTransactionManager,
            EmbeddingService embeddingService,
            ImpromptuProperties properties) {
        var store = new DrivineStore(
                persistenceManager,
                properties.neoRag(),
                embeddingService,
                platformTransactionManager,
                new DrivineCypherSearch(persistenceManager)
        );
        store.provision();
        return store;
    }

}
