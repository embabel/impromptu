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
package com.embabel.impromptu;

import com.embabel.agent.spi.LlmService;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ConfigurableModelProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Test configuration that provides mock LLM and embedding beans for tests that don't
 * need actual LLM functionality but require the Spring context to load.
 * <p>
 * This configuration is only used when real LLM beans are not available (e.g., CI
 * environments without API keys). Tests that actually need LLM functionality should
 * use the *IT suffix and be run manually with API keys configured.
 */
@TestConfiguration
public class TestLlmConfiguration {

    /**
     * A simple fake ChatModel that returns empty JSON responses.
     * Used for tests where we don't actually call the LLM.
     */
    static class FakeChatModel implements ChatModel {
        private final ChatOptions defaultOptions;

        FakeChatModel() {
            this.defaultOptions = new ChatOptions() {
                @Override
                public String getModel() {
                    return "fake-model";
                }

                @Override
                public Double getFrequencyPenalty() {
                    return null;
                }

                @Override
                public Integer getMaxTokens() {
                    return null;
                }

                @Override
                public Double getPresencePenalty() {
                    return null;
                }

                @Override
                public List<String> getStopSequences() {
                    return null;
                }

                @Override
                public Double getTemperature() {
                    return 0.0;
                }

                @Override
                public Integer getTopK() {
                    return null;
                }

                @Override
                public Double getTopP() {
                    return null;
                }

                @Override
                public ChatOptions copy() {
                    return this;
                }
            };
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return defaultOptions;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))));
        }
    }

    /**
     * A fake EmbeddingModel for Spring AI compatibility.
     * DrivineStore.createVectorIndex() needs this to get dimensions.
     */
    static class FakeEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of());
        }

        @Override
        public float[] embed(String text) {
            return new float[1536];
        }

        @Override
        public float[] embed(Document document) {
            return new float[1536];
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> new float[1536]).toList();
        }

        @Override
        public EmbeddingResponse embedForResponse(List<String> texts) {
            return new EmbeddingResponse(List.of());
        }

        @Override
        public int dimensions() {
            return 1536;
        }
    }

    /**
     * A simple fake EmbeddingService for tests.
     */
    static class FakeEmbeddingService implements EmbeddingService {
        private final FakeEmbeddingModel model = new FakeEmbeddingModel();

        @Override
        public String getName() {
            return "text-embedding-3-small";
        }

        @Override
        public String getProvider() {
            return "TestProvider";
        }

        @Override
        public float[] embed(String text) {
            // Return a simple mock embedding (1536 dimensions like text-embedding-3-small)
            return new float[1536];
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream()
                    .map(t -> new float[1536])
                    .toList();
        }

        @Override
        public int getDimensions() {
            return 1536;
        }

        @Override
        public Object getModel() {
            return model;
        }
    }

    /**
     * Override the default LLM service with a fake one.
     */
    @Bean(name = "testLlmService")
    @Primary
    public LlmService<?> testLlmService() {
        return new SpringAiLlmService(
                "gpt-4.1-mini",
                "TestProvider",
                new FakeChatModel()
        );
    }

    /**
     * Override the embedding service bean from RagConfiguration.
     * Uses the same bean name to override via spring.main.allow-bean-definition-overriding=true
     */
    @Bean(name = "embeddingService")
    @Primary
    public EmbeddingService embeddingService() {
        return new FakeEmbeddingService();
    }

    /**
     * Override the model provider with a simple implementation that doesn't have circular deps.
     * Uses the same bean name to override via spring.main.allow-bean-definition-overriding=true
     */
    @Bean(name = "configurableModelProvider")
    @Primary
    public ModelProvider configurableModelProvider() {
        var testLlm = new SpringAiLlmService(
                "gpt-4.1-mini",
                "TestProvider",
                new FakeChatModel()
        );
        var testEmbedding = new FakeEmbeddingService();
        var properties = new ConfigurableModelProviderProperties();
        properties.setDefaultLlm("gpt-4.1-mini");
        properties.setDefaultEmbeddingModel("text-embedding-3-small");
        return new ConfigurableModelProvider(
                List.of(testLlm),
                List.of(testEmbedding),
                properties
        );
    }

    /**
     * Provide a primary ObjectMapper to resolve ambiguity between
     * embabelJacksonObjectMapper and hillaEndpointObjectMapper in CI.
     */
    @Bean
    @Primary
    public ObjectMapper primaryObjectMapper() {
        return new ObjectMapper();
    }
}
