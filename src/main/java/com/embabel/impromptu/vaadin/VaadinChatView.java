package com.embabel.impromptu.vaadin;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.*;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.embabel.impromptu.proposition.ConversationAnalysisRequestEvent;
import com.embabel.impromptu.spotify.SpotifyService;
import com.embabel.impromptu.user.ImpromptuUserService;
import com.embabel.impromptu.vaadin.components.ChatFooter;
import com.embabel.impromptu.vaadin.components.ChatHeader;
import com.embabel.impromptu.vaadin.components.ChatMessageBubble;
import com.embabel.impromptu.vaadin.components.SpotifyPlayerPanel;
import com.embabel.impromptu.vaadin.components.VoiceControl;
import com.embabel.web.vaadin.components.EntityCard;
import com.embabel.web.vaadin.components.PropositionsPanel;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
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
 * Provides a browser-based chat interface.
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
    private final String persona;

    private VerticalLayout messagesLayout;
    private PropositionsPanel propositionsPanel;
    private TextField inputField;
    private Button sendButton;
    private VoiceControl voiceControl;

    public VaadinChatView(
            Chatbot chatbot,
            ImpromptuProperties properties,
            DrivineStore searchOperations,
            PropositionRepository propositionRepository,
            NamedEntityDataRepository entityRepository,
            ImpromptuUserService userService,
            SpotifyService spotifyService,
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
        this.persona = properties.voice() != null ? properties.voice().persona() : "Assistant";

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Build header
        var user = userService.getAuthenticatedUser();
        var stats = searchOperations.info();
        var headerConfig = new ChatHeader.HeaderConfig(
                user,
                properties.objective(),
                persona,
                stats.getChunkCount(),
                stats.getDocumentCount(),
                spotifyService.isConfigured(),
                spotifyService.isLinked(user)
        );
        add(new ChatHeader(headerConfig));

        // Messages container with scroller
        messagesLayout = new VerticalLayout();
        messagesLayout.setWidthFull();
        messagesLayout.setPadding(false);
        messagesLayout.setSpacing(true);

        var scroller = new Scroller(messagesLayout);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-base-color)");
        add(scroller);
        setFlexGrow(1, scroller);

        // Input section
        add(createInputSection());

        // Propositions panel
        propositionsPanel = new PropositionsPanel(propositionRepository);
        propositionsPanel.setOnMentionClick(this::showEntityDetail);
        add(propositionsPanel);

        // Spotify player (only if linked)
        if (spotifyService.isLinked(user)) {
            add(new SpotifyPlayerPanel(spotifyService, user));
        }

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
            var user = userService.getAuthenticatedUser();
            var chatSession = chatbot.createSession(user, outputChannel, UUID.randomUUID().toString());
            sessionData = new SessionData(chatSession, responseQueue);
            vaadinSession.setAttribute("sessionData", sessionData);
            logger.info("Created new chat session for user: {}", user.getDisplayName());
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
        voiceControl.setAutoSpeak(userService.getAuthenticatedUser().isVoiceEnabled());

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
                        // Speak the response if voice output is enabled
                        voiceControl.speak(content);
                    } else {
                        messagesLayout.add(ChatMessageBubble.error("Response timed out"));
                    }
                    scrollToBottom();
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.focus();

                    // Refresh propositions after a delay
                    propositionsPanel.scheduleRefresh(ui, 2000);
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
        var user = userService.getAuthenticatedUser();
        var conversation = sessionData.chatSession().getConversation();
        logger.info("Publishing ConversationAnalysisRequestEvent for user: {}", user.getDisplayName());
        eventPublisher.publishEvent(new ConversationAnalysisRequestEvent(this, user, conversation));

        // Schedule a refresh of propositions after analysis
        getUI().ifPresent(ui -> propositionsPanel.scheduleRefresh(ui, 2000));
    }

    private void scrollToBottom() {
        messagesLayout.getElement().executeJs("this.scrollTop = this.scrollHeight");
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
            logger.info("OutputChannel.send() called with event type: {}", event.getClass().getSimpleName());
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                var msg = msgEvent.getMessage();
                logger.info("MessageOutputChannelEvent received, message type: {}", msg.getClass().getSimpleName());
                if (msg instanceof AssistantMessage) {
                    logger.info("Queueing AssistantMessage: {}", msg.getContent().substring(0, Math.min(50, msg.getContent().length())));
                    queue.offer(msg);
                }
            }
        }
    }
}
