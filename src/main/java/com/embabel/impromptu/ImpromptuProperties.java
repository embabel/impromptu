package com.embabel.impromptu;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties for chatbot
 *
 * @param chatLlm       LLM model and hyperparameters to use
 * @param objective     the goal of the chatbot's responses: For example, to answer legal questions
 * @param voice         the persona and output style of the chatbot while achieving its objective
 * @param chunkerConfig configuration for ingestion
 * @param uiPort        port for Javelit web UI (default 8888)
 */
@ConfigurationProperties(prefix = "ragbot")
public record ImpromptuProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        String objective,
        @NestedConfigurationProperty Voice voice,
        @NestedConfigurationProperty ContentChunker.DefaultConfig chunkerConfig,
        @DefaultValue("8888") int uiPort,
        @DefaultValue("classpath:ui/chat.css") String uiCssPath
) {

    public record Voice(
            String persona,
            int maxWords
    ) {
    }
}
