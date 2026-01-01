package com.embabel.impromptu.vaadin;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.api.identity.SimpleUser;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.chat.*;
import com.embabel.impromptu.ImpromptuProperties;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Vaadin-based chat view for the RAG chatbot.
 * Provides a browser-based chat interface.
 */
@Route("chat")
@PageTitle("Embabel RAG Chat")
public class VaadinChatView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(VaadinChatView.class);

    private static final User ANONYMOUS_USER = new SimpleUser(
            "anonymous",
            "Anonymous User",
            "anonymous",
            null
    );

    private final Chatbot chatbot;
    private final ImpromptuProperties properties;
    private final LuceneSearchOperations searchOperations;

    private VerticalLayout messagesLayout;
    private TextField inputField;
    private Button sendButton;
    private ChatSession chatSession;
    private BlockingQueue<Message> responseQueue;
    private String persona;

    public VaadinChatView(
            Chatbot chatbot,
            ImpromptuProperties properties,
            LuceneSearchOperations searchOperations) {
        this.chatbot = chatbot;
        this.properties = properties;
        this.searchOperations = searchOperations;
        this.persona = properties.voice() != null ? properties.voice().persona() : "Assistant";

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        buildUI();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initChatSession();
    }

    private void initChatSession() {
        // Check if session already exists
        var session = VaadinSession.getCurrent();
        chatSession = (ChatSession) session.getAttribute("chatSession");
        responseQueue = (BlockingQueue<Message>) session.getAttribute("responseQueue");

        if (chatSession == null) {
            responseQueue = new ArrayBlockingQueue<>(10);
            var outputChannel = new QueueingOutputChannel(responseQueue);
            chatSession = chatbot.createSession(ANONYMOUS_USER, outputChannel, UUID.randomUUID().toString());
            session.setAttribute("chatSession", chatSession);
            session.setAttribute("responseQueue", responseQueue);
            logger.info("Created new chat session");
        }
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

        // Footer
        var footer = new Paragraph("Powered by Embabel Agent with RAG");
        footer.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-align", "center");
        add(footer);
    }

    private VerticalLayout createHeader() {
        var header = new VerticalLayout();
        header.setPadding(false);
        header.setSpacing(false);

        var title = new H3("Embabel RAG Chat");
        title.getStyle().set("margin", "0");

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

        header.add(title, statsText);
        return header;
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

        // Send to chatbot asynchronously
        var ui = getUI().orElse(null);
        if (ui == null) return;

        new Thread(() -> {
            try {
                var userMessage = new UserMessage(text);
                logger.info("Sending user message to chatSession: {}", text);
                chatSession.onUserMessage(userMessage);
                logger.info("onUserMessage returned, waiting for response from queue...");

                // Wait for response
                var response = responseQueue.poll(60, TimeUnit.SECONDS);
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
