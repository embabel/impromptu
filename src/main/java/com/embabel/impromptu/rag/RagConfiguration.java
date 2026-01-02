package com.embabel.impromptu.rag;

import com.embabel.agent.rag.neo.drivine.DrivineCypherSearch;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.impromptu.ImpromptuProperties;
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
    @Primary
    DrivineStore drivineStore(
            PersistenceManager persistenceManager,
            PlatformTransactionManager platformTransactionManager,
            ModelProvider modelProvider,
            ImpromptuProperties properties) {
        var embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
        return new DrivineStore(
                persistenceManager,
                properties.neoRag(),
                embeddingService,
                platformTransactionManager,
                new DrivineCypherSearch(persistenceManager)
        );
    }

}
