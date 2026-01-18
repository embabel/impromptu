package com.embabel.impromptu.vaadin;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.*;
import com.embabel.common.util.StringTrimmingUtilsKt;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.event.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.integrations.spotify.SpotifyService;
import com.embabel.impromptu.integrations.youtube.YouTubePendingPlayback;
import com.embabel.impromptu.integrations.youtube.YouTubeService;
import com.embabel.impromptu.proposition.persistence.DrivinePropositionRepository;
import com.embabel.impromptu.speech.PersonaService;
import com.embabel.impromptu.user.ImpromptuUser;
import com.embabel.impromptu.user.ImpromptuUserService;
import com.embabel.impromptu.vaadin.components.BackstagePanel;
import com.embabel.impromptu.vaadin.components.ChatFooter;
import com.embabel.impromptu.vaadin.components.ChatHeader;
import com.embabel.web.vaadin.components.ChatMessageBubble;
import com.embabel.web.vaadin.components.EntityCard;
import com.embabel.web.vaadin.components.VoiceControl;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
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
    private final String persona;
    private final ImpromptuUser currentUser;

    private VerticalLayout messagesLayout;
    private Scroller messagesScroller;
    private TextField inputField;
    private Button sendButton;
    private VoiceControl voiceControl;
    private BackstagePanel backstagePanel;

    public VaadinChatView(
            Chatbot chatbot,
            ImpromptuProperties properties,
            DrivineStore searchOperations,
            DrivinePropositionRepository propositionRepository,
            NamedEntityDataRepository entityRepository,
            ImpromptuUserService userService,
            SpotifyService spotifyService,
            YouTubeService youTubeService,
            YouTubePendingPlayback youTubePendingPlayback,
            PersonaService personaService,
            ApplicationEventPublisher eventPublisher,
            @Value("${database.datasources.neo.host:localhost}") String neo4jHost,
            @Value("${database.datasources.neo.port:7687}") int neo4jPort,
            @Value("${database.datasources.neo.user-name:neo4j}") String neo4jUsername,
            @Value("${database.datasources.neo.password:neo4j}") String neo4jPassword,
            @Value("${neo4j.http.port:7474}") int neo4jHttpPort) {
        this.chatbot = chatbot;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.entityRepository = entityRepository;
        this.youTubeService = youTubeService;
        this.youTubePendingPlayback = youTubePendingPlayback;
        this.personaService = personaService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        this.currentUser = userService.getAuthenticatedUser();
        // Use user's defaultVoice preference if set, otherwise fall back to default
        var defaultPersona = properties.defaultVoice() != null ? properties.defaultVoice().persona() : "impromptu";
        this.persona = currentUser.getVoice() != null ? currentUser.getVoice() : defaultPersona;
        var stats = searchOperations.info();

        // Build header
        var headerConfig = new ChatHeader.HeaderConfig(
                currentUser,
                properties.objective(),
                persona,
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

        // Backstage panel
        var indexStats = new com.embabel.impromptu.vaadin.components.ReferencesPanel.IndexStats(
                stats.getChunkCount(), stats.getDocumentCount());
        var backstageConfig = new BackstagePanel.Config(
                currentUser,
                spotifyService,
                youTubeService,
                entityRepository,
                propositionRepository,
                personaService,
                userService,
                this::showEntityDetail,
                indexStats,
                properties
        );
        backstagePanel = new BackstagePanel(backstageConfig);
        getElement().appendChild(backstagePanel.getElement());

        // Footer
        var neo4jConfig = new ChatFooter.Neo4jConfig(
                neo4jHost, neo4jPort, neo4jUsername, neo4jPassword, neo4jHttpPort
        );
        add(new ChatFooter(neo4jConfig, this::analyzeConversation));
    }

    /**
     * Lazily creates the chat session on first message send.
     */
    private record SessionData(ChatSession chatSession, BlockingQueue<Message> responseQueue) {
    }

    private SessionData getOrCreateSession() {
        var vaadinSession = VaadinSession.getCurrent();
        var sessionData = (SessionData) vaadinSession.getAttribute("sessionData");

        if (sessionData == null) {
            var responseQueue = new ArrayBlockingQueue<Message>(10);
            var outputChannel = new QueueingOutputChannel(responseQueue);
            var chatSession = chatbot.createSession(currentUser, outputChannel, UUID.randomUUID().toString());
            sessionData = new SessionData(chatSession, responseQueue);
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
        inputField.addKeyPressListener(Key.ENTER, e -> sendMessage());

        sendButton = new Button("Send", VaadinIcon.PAPERPLANE.create());
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> sendMessage());

        inputSection.add(voiceControl, inputField, sendButton);
        inputSection.setFlexGrow(1, inputField);

        return inputSection;
    }

    private void showUserProfileDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("User Settings");
        dialog.setWidth("400px");

        var content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // User name display
        var userNameLabel = new Span(currentUser.getDisplayName());
        userNameLabel.getStyle().set("font-weight", "bold");
        userNameLabel.getStyle().set("font-size", "var(--lumo-font-size-l)");
        content.add(userNameLabel);

        // Voice/Persona selection
        var voiceSelect = new Select<PersonaService.PersonaInfo>();
        voiceSelect.setLabel("Voice");
        voiceSelect.setWidthFull();
        voiceSelect.setItemLabelGenerator(PersonaService.PersonaInfo::displayName);

        // Custom renderer to show description in dropdown
        voiceSelect.setRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(persona -> {
            var item = new VerticalLayout();
            item.setPadding(false);
            item.setSpacing(false);
            item.getStyle().set("padding", "var(--lumo-space-xs) 0");

            var name = new Span(persona.displayName());
            name.getStyle().set("font-weight", "500");

            var desc = new Span(persona.description());
            desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
            desc.getStyle().set("font-size", "var(--lumo-font-size-s)");

            item.add(name, desc);
            return item;
        }));

        var personas = personaService.getAvailablePersonas();
        voiceSelect.setItems(personas);

        personas.stream()
                .filter(p -> p.name().equals(currentUser.getVoice()))
                .findFirst()
                .ifPresent(voiceSelect::setValue);

        content.add(voiceSelect);

        dialog.add(content);

        // Save button
        var saveButton = new Button("Save", e -> {
            var selected = voiceSelect.getValue();
            if (selected != null && !selected.name().equals(currentUser.getVoice())) {
                currentUser.setVoice(selected.name());
                userService.save(currentUser);
                logger.info("Updated user voice to: {}", selected.name());
                com.vaadin.flow.component.notification.Notification.show(
                        "Voice changed to " + selected.name() + ". Refresh to apply.",
                        3000,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
                );
            }
            dialog.close();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void onVoiceInput(String text) {
        if (text != null && !text.isBlank()) {
            inputField.setValue(text);
            sendMessage();
        }
    }

    private void sendMessage() {
        var text = inputField.getValue();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        inputField.clear();
        inputField.setEnabled(false);
        sendButton.setEnabled(false);

        // Add user message to UI
        messagesLayout.add(ChatMessageBubble.user(text));
        scrollToBottom();

        // Get or create session
        var sessionData = getOrCreateSession();

        // Send to chatbot asynchronously
        var ui = getUI().orElse(null);
        if (ui == null) return;

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
                        messagesLayout.add(ChatMessageBubble.assistant(persona, content));
                        // Speak the response if defaultVoice output is enabled
                        voiceControl.speak(content);
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
                    backstagePanel.getPropositionsPanel().scheduleRefresh(ui, 2000);
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
        getUI().ifPresent(ui -> backstagePanel.getPropositionsPanel().scheduleRefresh(ui, 2000));
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
                messagesLayout.add(ChatMessageBubble.assistant(persona, message.getContent()));
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
        var ytPanel = backstagePanel.getYouTubePlayerPanel();
        if (ytPanel == null) return;

        var video = youTubePendingPlayback.consumePendingVideo(currentUser.getId());
        if (video != null) {
            logger.info("Loading pending YouTube video: {} - {}", video.videoId(), video.title());
            ytPanel.loadVideo(video.videoId(), video.title(), video.channelTitle());
        }
    }

    /**
     * Show entity detail dialog when a mention is clicked.
     */
    private void showEntityDetail(EntityMention mention) {
        if (mention.getResolvedId() == null) {
            return;
        }

        var entity = entityRepository.findEntityById(mention.getResolvedId());
        if (entity == null) {
            logger.warn("Entity not found: {}", mention.getResolvedId());
            com.vaadin.flow.component.notification.Notification.show(
                    "Entity not found: " + mention.getResolvedId(),
                    3000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
            );
            return;
        }

        var dialog = new Dialog();
        dialog.setHeaderTitle(entity.getName());
        dialog.setWidth("400px");

        var content = new VerticalLayout();
        content.setPadding(false);
        content.add(new EntityCard(entity));

        dialog.add(content);
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    /**
     * OutputChannel that queues assistant messages for retrieval.
     */
    private record QueueingOutputChannel(BlockingQueue<Message> queue) implements OutputChannel {
        @Override
        public void send(OutputChannelEvent event) {
            logger.debug("OutputChannel.send() called with event type: {}", event.getClass().getSimpleName());
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                var msg = msgEvent.getMessage();
                logger.debug("MessageOutputChannelEvent received, message type: {}", msg.getClass().getSimpleName());
                if (msg instanceof AssistantMessage) {
                    logger.debug("Queueing AssistantMessage: {}",
                            StringTrimmingUtilsKt.trim(msg.getContent(), 80, 3, "..."));
                    queue.offer(msg);
                }
            }
        }
    }
}
