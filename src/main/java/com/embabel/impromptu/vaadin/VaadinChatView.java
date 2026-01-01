package com.embabel.impromptu.vaadin;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.api.identity.SimpleUser;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.chat.*;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.impromptu.ImpromptuProperties;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Chatbot chatbot;
    private final ImpromptuProperties properties;
    private final LuceneSearchOperations searchOperations;
    private final PropositionRepository propositionRepository;

    private VerticalLayout messagesLayout;
    private VerticalLayout propositionsContent;
    private Span propositionCountSpan;
    private TextField inputField;
    private Button sendButton;
    private String persona;

    public VaadinChatView(
            Chatbot chatbot,
            ImpromptuProperties properties,
            LuceneSearchOperations searchOperations,
            PropositionRepository propositionRepository) {
        this.chatbot = chatbot;
        this.properties = properties;
        this.searchOperations = searchOperations;
        this.propositionRepository = propositionRepository;
        this.persona = properties.voice() != null ? properties.voice().persona() : "Assistant";

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        buildUI();
    }

    /**
     * Lazily creates the chat session on first message send.
     * This ensures Spring context is fully initialized (all @Actions registered)
     * and properly scopes the session to VaadinSession for multi-user support.
     */
    private record SessionData(ChatSession chatSession, BlockingQueue<Message> responseQueue) {
    }

    /**
     * Gets the authenticated user from Google OAuth, or an anonymous user if not authenticated.
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            // Google OAuth provides these attributes
            String id = oauth2User.getAttribute("sub"); // Google's unique user ID
            String displayName = oauth2User.getAttribute("name");
            String email = oauth2User.getAttribute("email");
            // Use email as username for Google OAuth
            return new SimpleUser(
                    id != null ? id : UUID.randomUUID().toString(),
                    displayName != null ? displayName : "User",
                    email != null ? email : "unknown",
                    email
            );
        }
        // Return anonymous user
        return new SimpleUser(UUID.randomUUID().toString(), "Anonymous", "anonymous", null);
    }

    private SessionData getOrCreateSession() {
        var vaadinSession = VaadinSession.getCurrent();
        var sessionData = (SessionData) vaadinSession.getAttribute("sessionData");

        if (sessionData == null) {
            var responseQueue = new ArrayBlockingQueue<Message>(10);
            var outputChannel = new QueueingOutputChannel(responseQueue);
            var user = getAuthenticatedUser();
            var chatSession = chatbot.createSession(user, outputChannel, UUID.randomUUID().toString());
            sessionData = new SessionData(chatSession, responseQueue);
            vaadinSession.setAttribute("sessionData", sessionData);
            logger.info("Created new chat session for user: {}", user.getDisplayName());
        }

        return sessionData;
    }

    private void buildUI() {
        // Header section
        var header = createHeader();
        add(header);

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
        var inputSection = createInputSection();
        add(inputSection);

        // Propositions panel (collapsible)
        var propositionsPanel = createPropositionsPanel();
        add(propositionsPanel);

        // Footer with Embabel logo
        var footer = createFooter();
        add(footer);
    }

    private Details createPropositionsPanel() {
        // Header with count and refresh button
        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setSpacing(true);

        var titleSpan = new Span("Knowledge Base");
        titleSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-m)");

        propositionCountSpan = new Span("(0 propositions)");
        propositionCountSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh propositions");
        refreshButton.addClickListener(e -> refreshPropositions());

        headerLayout.add(titleSpan, propositionCountSpan, refreshButton);

        // Content area for propositions
        propositionsContent = new VerticalLayout();
        propositionsContent.setPadding(false);
        propositionsContent.setSpacing(true);
        propositionsContent.setWidthFull();

        var contentScroller = new Scroller(propositionsContent);
        contentScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        contentScroller.setHeight("200px");
        contentScroller.setWidthFull();
        contentScroller.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-shade-5pct)");

        var details = new Details(headerLayout, contentScroller);
        details.addThemeVariants(DetailsVariant.FILLED);
        details.setWidthFull();
        details.getStyle()
                .set("--vaadin-details-summary-padding", "var(--lumo-space-s) var(--lumo-space-m)");

        // Refresh when opened
        details.addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                refreshPropositions();
            }
        });

        return details;
    }

    private void schedulePropositionRefresh(com.vaadin.flow.component.UI ui) {
        // Schedule refresh after 2 seconds to allow async proposition extraction to complete
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                ui.access(this::refreshPropositions);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void refreshPropositions() {
        propositionsContent.removeAll();

        var propositions = propositionRepository.findAll();
        propositionCountSpan.setText("(" + propositions.size() + " propositions)");

        if (propositions.isEmpty()) {
            var emptyMessage = new Span("No propositions extracted yet. Start a conversation to build the knowledge base.");
            emptyMessage.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic")
                    .set("padding", "var(--lumo-space-m)");
            propositionsContent.add(emptyMessage);
            return;
        }

        // Sort by created time (newest first)
        propositions.stream()
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .forEach(prop -> propositionsContent.add(createPropositionCard(prop)));
    }

    private Div createPropositionCard(Proposition prop) {
        var card = new Div();
        card.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("margin-bottom", "var(--lumo-space-xs)");

        // Proposition text
        var textSpan = new Span(prop.getText());
        textSpan.getStyle()
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        // Metadata line
        var metaLayout = new HorizontalLayout();
        metaLayout.setSpacing(true);
        metaLayout.getStyle().set("flex-wrap", "wrap");

        // Confidence badge
        var confidencePercent = (int) (prop.getConfidence() * 100);
        var confidenceSpan = new Span(confidencePercent + "% confidence");
        confidenceSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", confidencePercent >= 80 ? "var(--lumo-success-text-color)" :
                        confidencePercent >= 50 ? "var(--lumo-secondary-text-color)" :
                                "var(--lumo-error-text-color)")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "2px 8px")
                .set("border-radius", "var(--lumo-border-radius-s)");

        // Time
        var timeSpan = new Span(TIME_FORMATTER.format(prop.getCreated()));
        timeSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)");

        // Entity mentions
        var mentions = prop.getMentions();
        if (mentions != null && !mentions.isEmpty()) {
            var entityCount = new Span(mentions.size() + " entities");
            entityCount.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)");
            metaLayout.add(confidenceSpan, entityCount, timeSpan);
        } else {
            metaLayout.add(confidenceSpan, timeSpan);
        }

        card.add(textSpan, metaLayout);
        return card;
    }

    private VerticalLayout createHeader() {
        var header = new VerticalLayout();
        header.setPadding(false);
        header.setSpacing(false);

        // Title row with logout
        var titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setJustifyContentMode(JustifyContentMode.BETWEEN);

        var title = new H3("Impromptu Classical Music Explorer");
        title.getStyle().set("margin", "0");

        // User info and logout
        var userSection = new HorizontalLayout();
        userSection.setAlignItems(Alignment.CENTER);
        userSection.setSpacing(true);

        var user = getAuthenticatedUser();
        var userName = new Span(user.getDisplayName());
        userName.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        if (!"Anonymous".equals(user.getDisplayName())) {
            var logoutButton = new Button("Logout", e -> {
                getUI().ifPresent(ui -> {
                    ui.getPage().setLocation("/logout");
                });
            });
            logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            logoutButton.getStyle()
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
            userSection.add(userName, logoutButton);
        } else {
            var loginLink = new Anchor("/login", "Sign in");
            loginLink.getStyle()
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("text-decoration", "none");
            userSection.add(loginLink);
        }

        titleRow.add(title, userSection);

        // Stats line
        var stats = searchOperations.info();
        var statsText = new Span(String.format(
                "Objective: %s | Persona: %s | %,d chunks | %,d documents",
                properties.objective() != null ? properties.objective() : "Not set",
                persona,
                stats.getChunkCount(),
                stats.getDocumentCount()
        ));
        statsText.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        header.add(titleRow, statsText);
        return header;
    }

    private HorizontalLayout createFooter() {
        var footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.CENTER);
        footer.setAlignItems(Alignment.CENTER);
        footer.setSpacing(true);
        footer.getStyle().set("padding", "var(--lumo-space-s) 0");

        var logo = new Image(
                "https://docs.embabel.com/embabel-agent/guide/0.3.1/images/tower.png",
                "Embabel"
        );
        logo.setHeight("24px");
        logo.getStyle()
                .set("filter", "drop-shadow(0 0 4px rgba(201, 162, 39, 0.5))")
                .set("opacity", "0.85");

        var poweredBy = new Span("Powered by");
        poweredBy.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        var embabelLink = new Anchor("https://embabel.com", "Embabel");
        embabelLink.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("text-decoration", "none")
                .set("font-weight", "500");
        embabelLink.setTarget("_blank");

        footer.add(logo, poweredBy, embabelLink);
        return footer;
    }

    private HorizontalLayout createInputSection() {
        var inputSection = new HorizontalLayout();
        inputSection.setWidthFull();
        inputSection.setPadding(false);

        inputField = new TextField();
        inputField.setPlaceholder("Type your message...");
        inputField.setWidthFull();
        inputField.setClearButtonVisible(true);
        inputField.addKeyPressListener(Key.ENTER, e -> sendMessage());

        sendButton = new Button("Send", VaadinIcon.PAPERPLANE.create());
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> sendMessage());

        inputSection.add(inputField, sendButton);
        inputSection.setFlexGrow(1, inputField);

        return inputSection;
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
        addUserMessage(text);

        // Get or create session (lazy - ensures Spring is fully initialized)
        var sessionData = getOrCreateSession();

        // Send to chatbot asynchronously
        var ui = getUI().orElse(null);
        if (ui == null) return;

        new Thread(() -> {
            try {
                var userMessage = new UserMessage(text);
                logger.info("Sending user message to chatSession: {}", text);
                sessionData.chatSession().onUserMessage(userMessage);
                logger.info("onUserMessage returned, waiting for response from queue...");

                // Wait for response
                var response = sessionData.responseQueue().poll(60, TimeUnit.SECONDS);
                logger.info("Poll returned: {}", response != null ? "got response" : "null/timeout");

                logger.info("About to call ui.access() with response: {}", response != null);
                ui.access(() -> {
                    logger.info("Inside ui.access() callback");
                    if (response != null) {
                        logger.info("Adding assistant message to UI");
                        addAssistantMessage(response.getContent());
                    } else {
                        addErrorMessage("Response timed out");
                    }
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.focus();

                    // Refresh propositions after a delay to allow async extraction to complete
                    schedulePropositionRefresh(ui);
                });
                logger.info("ui.access() returned");
            } catch (Exception e) {
                logger.error("Error getting chatbot response", e);
                ui.access(() -> {
                    addErrorMessage("Error: " + e.getMessage());
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void addUserMessage(String text) {
        var messageDiv = createMessageDiv("You", text, true);
        messagesLayout.add(messageDiv);
        scrollToBottom();
    }

    private void addAssistantMessage(String text) {
        var messageDiv = createMessageDiv(persona, text, false);
        messagesLayout.add(messageDiv);
        scrollToBottom();
    }

    private void addErrorMessage(String text) {
        var messageDiv = new Div();
        messageDiv.getStyle()
                .set("background", "var(--lumo-error-color-10pct)")
                .set("border-left", "3px solid var(--lumo-error-color)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin", "var(--lumo-space-xs) 0");
        messageDiv.setText(text);
        messagesLayout.add(messageDiv);
        scrollToBottom();
    }

    private Div createMessageDiv(String sender, String text, boolean isUser) {
        var container = new Div();
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", isUser ? "flex-end" : "flex-start")
                .set("width", "100%");

        var messageDiv = new Div();
        messageDiv.getStyle()
                .set("max-width", "80%")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin", "var(--lumo-space-xs) 0");

        if (isUser) {
            messageDiv.getStyle()
                    .set("background", "var(--lumo-primary-color)")
                    .set("color", "var(--lumo-primary-contrast-color)");
        } else {
            messageDiv.getStyle()
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-body-text-color)");
        }

        var senderSpan = new Span(sender);
        senderSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        var textSpan = new Span(text);
        textSpan.getStyle().set("white-space", "pre-wrap");

        messageDiv.add(senderSpan, textSpan);
        container.add(messageDiv);

        return container;
    }

    private void scrollToBottom() {
        messagesLayout.getElement().executeJs(
                "this.scrollTop = this.scrollHeight"
        );
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
