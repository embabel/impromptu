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
package com.embabel.impromptu.vaadin;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.api.channel.ProgressOutputChannelEvent;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.*;
import com.embabel.common.util.StringTrimmingUtilsKt;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.data.GraphExportService;
import com.embabel.impromptu.data.pipeline.*;
import com.embabel.impromptu.event.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubePendingPlayback;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.proposition.persistence.DrivinePropositionRepository;
import com.embabel.impromptu.rag.DocumentService;
import com.embabel.impromptu.speech.PersonaService;
import com.embabel.impromptu.theme.ThemeService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.impromptu.user.ImpromptuUserService;
import com.embabel.impromptu.vaadin.components.BackstagePanel;
import com.embabel.impromptu.vaadin.components.ChatFooter;
import com.embabel.impromptu.vaadin.components.ChatHeader;
import com.embabel.impromptu.vaadin.components.SessionPanel;
import com.embabel.web.vaadin.components.ChatMessageBubble;
import com.embabel.web.vaadin.components.InlineAssetCard;
import com.embabel.web.vaadin.components.VoiceControl;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Vaadin-based chat view for the RAG chatbot.
 * Provides a browser-based chat interface with side drawers for media and knowledge.
 */
@Route("chat")
@PageTitle("Impromptu Classical Music Explorer")
@PermitAll
public class VaadinChatView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(VaadinChatView.class);

    private final Chatbot chatbot;
    private final ImpromptuUserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final NamedEntityDataRepository entityRepository;
    private final YouTubeService youTubeService;
    private final YouTubePendingPlayback youTubePendingPlayback;
    private final PersonaService personaService;
    private final ThemeService themeService;
    private final ComposerEnhancementPipeline pipeline;
    private final ComposerInfluenceLoader influenceLoader;
    private final ComposerTechniqueEnhancer techniqueEnhancer;
    private final ComposerNationalityEnhancer nationalityEnhancer;
    private final WorkInstrumentationEnhancer workInstrumentationEnhancer;
    private final GraphExportService exportService;
    private final String defaultPersona;
    private final ImpromptuUser currentUser;

    private VerticalLayout messagesLayout;
    private Scroller messagesScroller;
    private TextField inputField;
    private Button sendButton;
    private VoiceControl voiceControl;
    private BackstagePanel backstagePanel;
    private SessionPanel sessionPanel;

    public VaadinChatView(
            Chatbot chatbot,
            ImpromptuProperties properties,
            DrivineStore searchOperations,
            DrivinePropositionRepository propositionRepository,
            DocumentService documentService,
            NamedEntityDataRepository entityRepository,
            ImpromptuUserService userService,
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            YouTubePendingPlayback youTubePendingPlayback,
            PersonaService personaService,
            ThemeService themeService,
            ComposerEnhancementPipeline pipeline,
            ComposerInfluenceLoader influenceLoader,
            ComposerTechniqueEnhancer techniqueEnhancer,
            ComposerNationalityEnhancer nationalityEnhancer,
            WorkInstrumentationEnhancer workInstrumentationEnhancer,
            GraphExportService exportService,
            ApplicationEventPublisher eventPublisher,
            @Value("${database.datasources.neo.host:localhost}") String neo4jHost,
            @Value("${database.datasources.neo.port:7687}") int neo4jPort,
            @Value("${database.datasources.neo.user-name:neo4j}") String neo4jUsername,
            @Value("${database.datasources.neo.password:neo4j}") String neo4jPassword,
            @Value("${neo4j.http.port:7474}") int neo4jHttpPort) {
        this.pipeline = pipeline;
        this.influenceLoader = influenceLoader;
        this.techniqueEnhancer = techniqueEnhancer;
        this.nationalityEnhancer = nationalityEnhancer;
        this.workInstrumentationEnhancer = workInstrumentationEnhancer;
        this.exportService = exportService;
        this.chatbot = chatbot;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.entityRepository = entityRepository;
        this.youTubeService = youTubeService;
        this.youTubePendingPlayback = youTubePendingPlayback;
        this.personaService = personaService;
        this.themeService = themeService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        this.currentUser = userService.getAuthenticatedUser();
        // Default persona from properties, used as fallback if user hasn't set a voice
        this.defaultPersona = properties.defaultPersonality() != null ? properties.defaultPersonality().persona() : "impromptu";

        // Apply initial theme after attach (ensures DOM is ready)
        addAttachListener(event -> {
            logger.info("Loading theme for user {}: {}", currentUser.getDisplayName(), currentUser.getTheme());
            applyTheme(currentUser.getTheme());
        });

        var stats = searchOperations.info();

        // Build header
        var headerConfig = new ChatHeader.HeaderConfig(
                currentUser,
                properties.objective(),
                stats.getChunkCount(),
                stats.getDocumentCount(),
                spotifyService.isConfigured(),
                spotifyService.isLinked(currentUser),
                this::showUserProfileDialog
        );
        add(new ChatHeader(headerConfig));

        // Messages container with scroller
        messagesLayout = new VerticalLayout();
        messagesLayout.setWidthFull();
        messagesLayout.setPadding(false);
        messagesLayout.setSpacing(true);

        messagesScroller = new Scroller(messagesLayout);
        messagesScroller.setSizeFull();
        messagesScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        messagesScroller.addClassName("chat-scroller");
        add(messagesScroller);
        setFlexGrow(1, messagesScroller);

        // Restore previous messages if session exists
        restorePreviousMessages();

        // Input section
        add(createInputSection());

        // Backstage panel (app-level content)
        var backstageConfig = new BackstagePanel.Config(
                entityRepository,
                documentService,
                properties,
                pipeline,
                influenceLoader,
                techniqueEnhancer,
                nationalityEnhancer,
                workInstrumentationEnhancer,
                exportService
        );
        backstagePanel = new BackstagePanel(backstageConfig);
        getElement().appendChild(backstagePanel.getElement());

        // Session panel (user/session-level content)
        var sessionConfig = new SessionPanel.Config(
                currentUser,
                properties,
                spotifyService,
                youTubeService,
                youTubePendingPlayback,
                propositionRepository,
                entityRepository,
                personaService,
                themeService,
                this::getAssetView,
                this::onPersonaChange,
                this::onMaxWordsChange,
                this::onThemeChange,
                this::onPresetChange,
                this::analyzeConversation
        );
        sessionPanel = new SessionPanel(sessionConfig);
        getElement().appendChild(sessionPanel.getElement());

        // Footer
        var neo4jConfig = new ChatFooter.Neo4jConfig(
                neo4jHost, neo4jPort, neo4jUsername, neo4jPassword, neo4jHttpPort
        );
        add(new ChatFooter(neo4jConfig));
    }

    /**
     * Lazily creates the chat session on first message send.
     */
    private record SessionData(ChatSession chatSession, BlockingQueue<Message> responseQueue,
                               VaadinOutputChannel outputChannel) {
    }

    private SessionData getOrCreateSession(UI ui) {
        var vaadinSession = VaadinSession.getCurrent();
        var sessionData = (SessionData) vaadinSession.getAttribute("sessionData");

        if (sessionData == null) {
            var responseQueue = new ArrayBlockingQueue<Message>(10);
            var outputChannel = new VaadinOutputChannel(responseQueue, ui);
            var chatSession = chatbot.createSession(currentUser, outputChannel, UUID.randomUUID().toString(), null);
            sessionData = new SessionData(chatSession, responseQueue, outputChannel);
            vaadinSession.setAttribute("sessionData", sessionData);
            logger.info("Created new chat session for user: {}", currentUser.getDisplayName());
        }

        return sessionData;
    }

    private HorizontalLayout createInputSection() {
        var inputSection = new HorizontalLayout();
        inputSection.setWidthFull();
        inputSection.setPadding(false);
        inputSection.setAlignItems(Alignment.CENTER);

        // Voice control - initialize from user preferences
        voiceControl = new VoiceControl();
        voiceControl.setOnSpeechRecognized(this::onVoiceInput);
        voiceControl.setAutoSpeak(currentUser.isVoiceEnabled());

        inputField = new TextField();
        inputField.setPlaceholder("Type or click mic to speak...");
        inputField.setWidthFull();
        inputField.setClearButtonVisible(true);
        inputField.getElement().setAttribute("autocomplete", "off");
        inputField.addKeyPressListener(Key.ENTER, e -> sendMessage());

        sendButton = new Button("Send", VaadinIcon.PAPERPLANE.create());
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> sendMessage());

        inputSection.add(voiceControl, inputField, sendButton);
        inputSection.setFlexGrow(1, inputField);

        return inputSection;
    }

    private void showUserProfileDialog() {
        sessionPanel.open();
    }

    /**
     * Handle personality change from the SessionPanel.
     */
    private void onPersonaChange(String personaName) {
        if (personaName != null && !personaName.equals(currentUser.getPersonality())) {
            currentUser.setPersonality(personaName);
            userService.save(currentUser);
            logger.info("Updated user personality to: {}", personaName);
            com.vaadin.flow.component.notification.Notification.show(
                    "Personality updated.",
                    3000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
            );
        }
    }

    /**
     * Handle max words change from the SessionPanel.
     */
    private void onMaxWordsChange(int maxWords) {
        if (maxWords != currentUser.getMaxWords()) {
            currentUser.setMaxWords(maxWords);
            userService.save(currentUser);
            logger.info("Updated user maxWords to: {}", maxWords);
        }
    }

    /**
     * Handle theme change from the SessionPanel.
     */
    private void onThemeChange(String themeName) {
        if (themeName != null && !themeName.equals(currentUser.getTheme())) {
            currentUser.setTheme(themeName);
            userService.save(currentUser);
            applyTheme(themeName);
            logger.info("Updated user theme to: {}", themeName);
            com.vaadin.flow.component.notification.Notification.show(
                    "Theme updated.",
                    3000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
            );
        }
    }

    /**
     * Handle preset change from the SessionPanel.
     * Applies theme, persona, and maxWords together.
     */
    private void onPresetChange(ImpromptuProperties.Preset preset) {
        if (preset == null) return;

        currentUser.applyPreset(preset);
        userService.save(currentUser);

        // Apply theme immediately
        if (preset.theme() != null) {
            applyTheme(preset.theme());
        }

        logger.info("Applied preset: theme={}, persona={}, maxWords={}",
                preset.theme(), preset.persona(), preset.maxWords());
        com.vaadin.flow.component.notification.Notification.show(
                "Preset applied: " + (preset.description() != null ? preset.description() : preset.theme()),
                3000,
                com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
        );
    }

    /**
     * Apply a theme by setting CSS variables directly on the document element.
     * This uses inline styles which have the highest CSS priority.
     */
    private void applyTheme(String themeName) {
        var theme = themeName != null ? themeName : "gold";
        var variables = themeService.getThemeVariables(theme);

        if (variables == null || variables.isEmpty()) {
            logger.warn("No variables found for theme: {}", theme);
            return;
        }

        // Build JavaScript to set each variable
        var js = new StringBuilder();
        js.append("(function() {\n");
        js.append("  var root = document.documentElement;\n");
        js.append("  var themeName = '").append(theme).append("';\n");
        js.append("  // Set theme class\n");
        js.append("  root.className = root.className.replace(/\\btheme-\\w+/g, '').trim();\n");
        js.append("  document.body.className = document.body.className.replace(/\\btheme-\\w+/g, '').trim();\n");
        js.append("  root.classList.add('theme-' + themeName);\n");
        js.append("  document.body.classList.add('theme-' + themeName);\n");
        js.append("  // Set CSS variables directly (inline style = highest priority)\n");

        for (var entry : variables.entrySet()) {
            String varName = entry.getKey();
            String varValue = entry.getValue().replace("'", "\\'");
            js.append("  root.style.setProperty('").append(varName).append("', '").append(varValue).append("');\n");
        }

        js.append("  console.log('Applied theme:', themeName, '(").append(variables.size()).append(" variables)');\n");
        js.append("})();");

        getUI().ifPresent(ui -> ui.getPage().executeJs(js.toString()));
    }

    private void onVoiceInput(String text) {
        if (text != null && !text.isBlank()) {
            inputField.setValue(text);
            sendMessage();
        }
    }

    /**
     * Get the current persona/voice name for the assistant.
     * Uses the user's preference, falling back to the default.
     */
    private String getPersona() {
        var userVoice = currentUser.getPersonality();
        return userVoice != null ? userVoice : defaultPersona;
    }

    private void sendMessage() {
        var text = inputField.getValue();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        var ui = getUI().orElse(null);
        if (ui == null) return;

        inputField.clear();
        inputField.setEnabled(false);
        sendButton.setEnabled(false);

        // Add user message to UI
        messagesLayout.add(ChatMessageBubble.user(text));
        scrollToBottom();

        // Get or create session (needs UI for progress indicators)
        var sessionData = getOrCreateSession(ui);

        // Track asset count before sending
        var assetView = getAssetView();
        int assetCountBefore = assetView != null ? assetView.getAssets().size() : 0;

        // Send to chatbot asynchronously
        new Thread(() -> {
            try {
                var userMessage = new UserMessage(text);
                logger.info("Sending user message to chatSession: {}", text);
                sessionData.chatSession().onUserMessage(userMessage);
                logger.debug("onUserMessage returned, waiting for response from queue...");

                var response = sessionData.responseQueue().poll(60, TimeUnit.SECONDS);
                logger.debug("Poll returned: {}", response != null ? "got response" : "null/timeout");

                ui.access(() -> {
                    if (response != null) {
                        var content = response.getContent();
                        messagesLayout.add(ChatMessageBubble.assistant(getPersona(), content));
                        // Speak the response if defaultVoice output is enabled
                        voiceControl.speak(content);

                        // Check for new assets and add inline cards
                        addInlineAssetCards(assetCountBefore);
                    } else {
                        messagesLayout.add(ChatMessageBubble.error("Response timed out"));
                    }
                    scrollToBottom();
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.focus();

                    // Check for pending YouTube playback
                    checkPendingYouTubePlayback();

                    // Refresh propositions after a delay
                    sessionPanel.getPropositionsPanel().scheduleRefresh(ui, 2000);
                });
            } catch (Exception e) {
                logger.error("Error getting chatbot response", e);
                ui.access(() -> {
                    messagesLayout.add(ChatMessageBubble.error("Error: " + e.getMessage()));
                    scrollToBottom();
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Add inline asset cards for any assets added since the given count.
     */
    private void addInlineAssetCards(int assetCountBefore) {
        var assetView = getAssetView();
        if (assetView == null) return;

        var assets = assetView.getAssets();
        if (assets.size() <= assetCountBefore) return;

        // Get new assets (they appear at the end of the list)
        var newAssets = assets.subList(assetCountBefore, assets.size());
        for (var asset : newAssets) {
            var card = new InlineAssetCard(
                    asset,
                    tool -> sessionPanel.invokeToolFromAsset(tool),
                    () -> sessionPanel.openToAssets()
            );
            messagesLayout.add(card);
            logger.info("Added inline asset card for: {}", asset.reference().getName());
        }
    }

    private void analyzeConversation() {
        var vaadinSession = VaadinSession.getCurrent();
        var sessionData = (SessionData) vaadinSession.getAttribute("sessionData");
        if (sessionData == null) {
            logger.info("No session data - nothing to analyze");
            return;
        }
        var conversation = sessionData.chatSession().getConversation();
        logger.info("Publishing ConversationAnalysisRequestEvent for user: {}", currentUser.getDisplayName());
        eventPublisher.publishEvent(new ConversationAnalysisRequestEvent(this, currentUser, conversation));

        // Schedule a refresh of propositions after analysis
        getUI().ifPresent(ui -> sessionPanel.getPropositionsPanel().scheduleRefresh(ui, 2000));
    }

    private void scrollToBottom() {
        messagesScroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    /**
     * Restore previous messages from an existing session.
     */
    private void restorePreviousMessages() {
        var vaadinSession = VaadinSession.getCurrent();
        var sessionData = (SessionData) vaadinSession.getAttribute("sessionData");
        if (sessionData == null) {
            return;
        }

        var conversation = sessionData.chatSession().getConversation();
        for (var message : conversation.getMessages()) {
            if (message instanceof UserMessage) {
                messagesLayout.add(ChatMessageBubble.user(message.getContent()));
            } else if (message instanceof AssistantMessage) {
                messagesLayout.add(ChatMessageBubble.assistant(getPersona(), message.getContent()));
            }
        }

        if (!conversation.getMessages().isEmpty()) {
            scrollToBottom();
        }
    }

    /**
     * Check for pending YouTube playback requests from LLM tools.
     */
    private void checkPendingYouTubePlayback() {
        var ytPanel = sessionPanel.getYouTubePlayerPanel();
        if (ytPanel == null) return;

        var video = youTubePendingPlayback.consumePendingVideo(currentUser.getId());
        if (video != null) {
            logger.info("Loading pending YouTube video: {} - {}", video.videoId(), video.title());
            ytPanel.loadVideo(video.videoId(), video.title(), video.channelTitle());
            showYouTubeNotification(video.title(), video.videoId());
        }
    }

    /**
     * Show a toast notification for YouTube video with options to view.
     */
    private void showYouTubeNotification(String title, String videoId) {
        var notification = new com.vaadin.flow.component.notification.Notification();
        notification.setPosition(com.vaadin.flow.component.notification.Notification.Position.BOTTOM_END);
        notification.setDuration(8000);

        var layout = new HorizontalLayout();
        layout.setAlignItems(Alignment.CENTER);
        layout.setSpacing(true);

        var icon = VaadinIcon.PLAY_CIRCLE.create();
        icon.setColor("var(--lumo-error-color)");

        var text = new Span("▶ " + title);
        text.getStyle().set("font-weight", "500");

        var openButton = new Button("Watch", e -> {
            getUI().ifPresent(ui -> ui.getPage().open(
                    "https://www.youtube.com/watch?v=" + videoId, "_blank"));
            notification.close();
        });
        openButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        var backstageButton = new Button("Backstage", e -> {
            backstagePanel.open();
            notification.close();
        });
        backstageButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        layout.add(icon, text, openButton, backstageButton);
        notification.add(layout);
        notification.open();
    }

    /**
     * Get the AssetView from the current conversation, or null if no session exists.
     */
    private AssetView getAssetView() {
        var vaadinSession = VaadinSession.getCurrent();
        var sessionData = (SessionData) vaadinSession.getAttribute("sessionData");
        if (sessionData == null) {
            return null;
        }
        return sessionData.chatSession().getConversation().getAssetTracker();
    }

    /**
     * OutputChannel that queues assistant messages and displays tool call progress in real-time.
     */
    private class VaadinOutputChannel implements OutputChannel {
        private final BlockingQueue<Message> queue;
        private final UI ui;
        private Div currentToolCallIndicator;

        VaadinOutputChannel(BlockingQueue<Message> queue, UI ui) {
            this.queue = queue;
            this.ui = ui;
        }

        @Override
        public void send(OutputChannelEvent event) {
            logger.debug("OutputChannel.send() called with event type: {}", event.getClass().getSimpleName());
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                var msg = msgEvent.getMessage();
                logger.debug("MessageOutputChannelEvent received, message type: {}", msg.getClass().getSimpleName());
                if (msg instanceof AssistantMessage) {
                    // Remove tool call indicator before showing response
                    ui.access(() -> {
                        if (currentToolCallIndicator != null) {
                            messagesLayout.remove(currentToolCallIndicator);
                            currentToolCallIndicator = null;
                        }
                    });
                    logger.debug("Queueing AssistantMessage: {}",
                            StringTrimmingUtilsKt.trim(msg.getContent(), 80, 3, "..."));
                    queue.offer(msg);
                }
            } else if (event instanceof ProgressOutputChannelEvent progressEvent) {
                var message = progressEvent.getMessage();
                // LLM calls start with "Calling LLM", tool calls start with "🔧"
                boolean isLlmCall = message != null && message.startsWith("Calling LLM");
                boolean isToolCall = message != null && message.startsWith("🔧");

                ui.access(() -> {
                    // Remove previous indicator if exists
                    if (currentToolCallIndicator != null) {
                        messagesLayout.remove(currentToolCallIndicator);
                    }

                    // Determine what to show
                    String displayMessage;
                    if (currentUser.isShowToolCalls()) {
                        // Show full details for both LLM and tool calls
                        displayMessage = message;
                    } else if (isLlmCall) {
                        // Show LLM calls even when tool calls are hidden
                        displayMessage = message;
                    } else if (isToolCall) {
                        // Show minimal indicator for tool calls when hidden
                        displayMessage = "...";
                    } else {
                        // Other progress events - show as-is
                        displayMessage = message;
                    }

                    // Create progress indicator
                    currentToolCallIndicator = new Div();
                    currentToolCallIndicator.addClassName("tool-call-indicator");
                    if ("...".equals(displayMessage)) {
                        currentToolCallIndicator.addClassName("minimal-indicator");
                    }
                    currentToolCallIndicator.setText(displayMessage);
                    messagesLayout.add(currentToolCallIndicator);
                    scrollToBottom();
                });
            }
        }
    }
}
