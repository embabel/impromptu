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
package com.embabel.impromptu.chat;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.agent.rag.neo.drivine.CypherQueryTools;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import com.embabel.agent.tools.mcp.McpToolFactory;
import com.embabel.chat.Chatbot;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.dice.common.Relations;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.projection.memory.support.DefaultMemoryProjector;
import com.embabel.dice.projection.memory.support.RelationBasedKnowledgeTypeClassifier;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.integrations.imslp.ImslpTools;
import com.embabel.impromptu.integrations.metmuseum.MetMuseumTools;
import com.embabel.impromptu.resource.ResourceDelivery;
import com.embabel.impromptu.resource.ResourceGenerationService;
import com.embabel.impromptu.resource.ResourceTools;
import com.embabel.impromptu.rag.DocumentService;
import com.embabel.impromptu.user.DrivineImpromptuUserService;
import com.embabel.impromptu.user.ImpromptuUserService;
import io.modelcontextprotocol.client.McpSyncClient;
import org.drivine.manager.GraphObjectManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configure a chatbot that responds uses all actions available on the AgentPlatform
 */
@Configuration
class ChatConfiguration {

    /**
     * Allows us to use MCP tools in the chatbot
     * as matryoshka tools.
     * Tools must be configured in application.yml
     */
    @Bean
    McpToolFactory mcpToolFactory(
            List<McpSyncClient> clients) {
        return new McpToolFactory(clients);
    }

    @Bean
    Chatbot chatbot(
            AgentPlatform agentPlatform,
            ImpromptuProperties properties) {
        return AgentProcessChatbot.utilityFromPlatform(
                agentPlatform,
                new Verbosity().withShowPrompts(properties.showChatPrompts())
        );
    }

    @Bean
    ImpromptuUserService impromptuUserService(GraphObjectManager gom, ImpromptuProperties properties) {
        return new DrivineImpromptuUserService(gom, properties);
    }

    @Bean
    MemoryProjector memoryProjector(
            Relations relations) {
        return DefaultMemoryProjector
                .withKnowledgeTypeClassifier(new RelationBasedKnowledgeTypeClassifier(relations));
    }

    @Bean
    Tool sourcesTool(SearchOperations searchOperations, DocumentService documentService) {
        var docs = documentService.getDocuments();
        var description = "Search reference sources";
        if (!docs.isEmpty()) {
            var docList = docs.stream()
                    .map(DocumentService.DocumentInfo::title)
                    .collect(Collectors.joining(", "));
            description = "Search reference sources. Available: " + docList;
        }
        return new ToolishRag("sources", description, searchOperations)
                .withHint(TryHyDE.usingConversationContext());
    }

    /**
     * Common tools available to the chatbot regardless of user configuration.
     */
    public record CommonTools(List<Object> tools) {
    }

    @Bean
    CommonTools commonTools(
            McpToolFactory mcpToolFactory,
            ResourceGenerationService resourceGenerationService,
            ResourceDelivery resourceDelivery,
            CypherQueryTools cypherQueryTools) {
        var deter = "Use this tool only after trying the sources tool";
        return new CommonTools(List.of(
                mcpToolFactory.requiredToolByName("brave_web_search")
                        .withNote(deter),
                mcpToolFactory.matryoshkaByName(
                        "wikipedia",
                        "Search and find content from Wikipedia: " + deter,
                        Set.of("search_wikipedia", "get_article", "get_related_topics", "get_summary", "get_wikipedia_summary")),
                MetMuseumTools.DEFAULT,
                ImslpTools.DEFAULT,
                new ResourceTools(resourceGenerationService, resourceDelivery),
                cypherQueryTools.tool("""
                        Use this tool to query existing entities such as composers and works.
                        If you are asked questions like "Who composed the most violin concertos?" or
                        "List saxophone concertos" use this tool
                        """)
        ));
    }
}
