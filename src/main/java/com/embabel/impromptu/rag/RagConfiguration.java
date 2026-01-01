package com.embabel.impromptu.rag;

import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.impromptu.ImpromptuProperties;
import org.drivine.manager.PersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Paths;
import java.util.List;

@Configuration
@EnableConfigurationProperties(ImpromptuProperties.class)
class RagConfiguration {

    private final Logger logger = LoggerFactory.getLogger(RagConfiguration.class);

    @Bean
    @Primary
    SearchOperations searchOperations(PersistenceManager persistenceManager) {
        return new DrivineStore(persistenceManager, List.of(), )
    }

}
