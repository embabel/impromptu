package com.embabel.impromptu.javelit;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.api.identity.SimpleUser;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.chat.*;
import com.embabel.impromptu.ImpromptuProperties;
import io.javelit.core.Jt;
import io.javelit.core.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Javelit-based web UI for the RAG chatbot.
 * Provides a browser-based chat interface as an alternative to the shell.
 */
@Component
public record JavelitChatUI(
        Chatbot chatbot,
        ImpromptuProperties properties,
        LuceneSearchOperations searchOperations
) {
    private static final Logger logger = LoggerFactory.getLogger(JavelitChatUI.class);
    private static final AtomicReference<Server> serverRef = new AtomicReference<>();

    private static final User ANONYMOUS_USER = new SimpleUser(
            "anonymous",
            "Anonymous User",
            "anonymous",
            null
    );

    /**
     * Start the Javelit web server using the configured port.
     */
    public String start(boolean openBrowser) {
        var port = properties.uiPort();
        if (serverRef.get() != null) {
            return "Chat UI already running at http://localhost:" + port;
        }

        // Resolve CSS file path for headersFile
        var headersFilePath = resolveCssPath();
        var serverBuilder = Server.builder(this::app, port);
        if (headersFilePath != null) {
            logger.info("Using custom CSS from: {}", headersFilePath);
            serverBuilder.headersFile(headersFilePath);
        }
        var server = serverBuilder.build();
        server.start();
        serverRef.set(server);

        var url = "http://localhost:" + port;
        logger.info("Javelit Chat UI started at {}", url);

        if (openBrowser) {
            openInBrowser(url);
        }

        return url;
    }

    /**
     * Start the Javelit web server and open browser.
     */
    public String start() {
        return start(true);
    }

    private void openInBrowser(String url) {
        try {
            // Try Desktop API first
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                logger.info("Opened browser at {}", url);
                return;
            }
        } catch (Exception e) {
            logger.debug("Desktop API failed: {}", e.getMessage());
        }

        // Fallback to platform-specific commands
        try {
            var os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
            logger.info("Opened browser at {}", url);
        } catch (Exception e) {
            logger.warn("Failed to open browser: {}. Please open manually: {}", e.getMessage(), url);
        }
    }

    /**
     * Stop the Javelit web server.
     */
    public void stop() {
        var server = serverRef.getAndSet(null);
        if (server != null) {
            server.stop();
            logger.info("Javelit Chat UI stopped");
        }
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return serverRef.get() != null;
    }

    /**
     * The Javelit app definition - runs on each user interaction.
     */
    @SuppressWarnings("unchecked")
    private void app() {
        // Get or create session for this browser session
        var sessionState = Jt.sessionState();

        // Initialize message history for display
        var displayHistory = (List<Message>) sessionState
                .computeIfAbsent("displayHistory", key -> new ArrayList<>());

        // Get or create chat session
        var chatSession = (ChatSession) sessionState.computeIfAbsent("chatSession", key -> {
            var queue = new ArrayBlockingQueue<Message>(10);
            var outputChannel = new QueueingOutputChannel(queue);
            var session = chatbot.createSession(ANONYMOUS_USER, outputChannel, UUID.randomUUID().toString());
            sessionState.put("responseQueue", queue);
            return session;
        });

        var responseQueue = (BlockingQueue<Message>) sessionState.get("responseQueue");

        // Page title with persona name
        var persona = properties.voice() != null ? properties.voice().persona() : "Assistant";
        Jt.title(":speech_balloon: Embabel RAG chat").use();

        // Show objective and persona
        Jt.markdown(":dart: **Objective:** %s | :speaking_head: **Persona:** %s".formatted(
                properties.objective() != null ? properties.objective() : "Not set",
                persona
        )).key("objective-persona").use();

        // Show store stats
        var stats = searchOperations.info();
        Jt.markdown(":file_folder: **%s (%s):** %,d chunks | %,d documents".formatted(
                searchOperations.getName(),
                searchOperations.getClass().getSimpleName(),
                stats.getChunkCount(),
                stats.getDocumentCount()
        )).key("store-stats").use();
        Jt.markdown("---").key("stats-divider").use();

        // Create container for messages
        var msgContainer = Jt.container().use();

        // Display all previous messages
        for (var i = 0; i < displayHistory.size(); i++) {
            var message = displayHistory.get(i);
            if (message instanceof UserMessage) {
                Jt.markdown(":bust_in_silhouette: **You:** " + message.getContent())
                        .key("msg-" + i)
                        .use(msgContainer);
            } else if (message instanceof AssistantMessage) {
                Jt.markdown(":robot: **" + persona + ":** " + message.getContent())
                        .key("msg-" + i)
                        .use(msgContainer);
            }
        }

        // User input field
        var inputMessage = Jt.textInput("Your message:").use();

        // Process input when user submits
        if (inputMessage != null && !inputMessage.trim().isEmpty()) {
            var msgIndex = displayHistory.size();

            // Add user message to history and display
            var userMessage = new UserMessage(inputMessage);
            displayHistory.add(userMessage);
            Jt.markdown(":bust_in_silhouette: **You:** " + inputMessage)
                    .key("msg-" + msgIndex)
                    .use(msgContainer);

            // Send to chatbot
            try {
                chatSession.onUserMessage(userMessage);

                // Wait for response (with timeout)
                var response = responseQueue.poll(60, TimeUnit.SECONDS);
                if (response != null) {
                    displayHistory.add(response);
                    Jt.markdown(":robot: **" + persona + ":** " + response.getContent())
                            .key("msg-" + (msgIndex + 1))
                            .use(msgContainer);
                } else {
                    Jt.warning("Response timed out").use(msgContainer);
                }
            } catch (Exception e) {
                logger.error("Error getting chatbot response", e);
                Jt.error("Error: " + e.getMessage()).use(msgContainer);
            }
        }

        // Add a divider and info section
        Jt.markdown("---").key("footer-divider").use();
        Jt.markdown("_Powered by Embabel Agent with RAG_").key("footer-text").use();
    }

    private String resolveCssPath() {
        try {
            var resource = new DefaultResourceLoader().getResource(properties.uiCssPath());
            var file = resource.getFile();
            return file.getAbsolutePath();
        } catch (IOException e) {
            // For classpath resources, we need to extract to a temp file
            try {
                var resource = new DefaultResourceLoader().getResource(properties.uiCssPath());
                var tempFile = java.io.File.createTempFile("javelit-css-", ".html");
                tempFile.deleteOnExit();
                try (var in = resource.getInputStream();
                     var out = new java.io.FileOutputStream(tempFile)) {
                    in.transferTo(out);
                }
                logger.info("Extracted CSS to temp file: {}", tempFile.getAbsolutePath());
                return tempFile.getAbsolutePath();
            } catch (IOException ex) {
                logger.warn("Failed to resolve CSS path {}: {}", properties.uiCssPath(), ex.getMessage());
                return null;
            }
        }
    }

    /**
     * OutputChannel that queues assistant messages for retrieval.
     */
    private record QueueingOutputChannel(BlockingQueue<Message> queue) implements OutputChannel {
        @Override
        public void send(OutputChannelEvent event) {
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                var msg = msgEvent.getMessage();
                if (msg instanceof AssistantMessage) {
                    queue.offer(msg);
                }
            }
        }
    }
}
