package com.embabel.web.vaadin.components;

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Voice control component using Web Speech API.
 * Provides a microphone button for speech-to-text and can speak responses.
 */
@Tag("div")
@JsModule("./voice-control.js")
public class VoiceControl extends HorizontalLayout {

    private static final Logger logger = LoggerFactory.getLogger(VoiceControl.class);

    private final Button micButton;
    private final Button speakerButton;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private boolean autoSpeak = false;

    private Consumer<String> onSpeechRecognized;

    public VoiceControl() {
        setSpacing(false);
        setPadding(false);
        setAlignItems(Alignment.CENTER);

        // Microphone button
        micButton = new Button(createMicIcon(false));
        micButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        micButton.getElement().setAttribute("title", "Click to speak (voice input)");
        micButton.addClickListener(e -> toggleListening());

        // Speaker button (toggle auto-speak)
        speakerButton = new Button(createSpeakerIcon(false));
        speakerButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        speakerButton.getElement().setAttribute("title", "Toggle voice responses");
        speakerButton.addClickListener(e -> toggleAutoSpeak());

        add(micButton, speakerButton);

        // Check browser support on attach
        addAttachListener(e -> checkBrowserSupport());
    }

    private Icon createMicIcon(boolean active) {
        var icon = VaadinIcon.MICROPHONE.create();
        if (active) {
            icon.setColor("var(--lumo-error-color)");
        }
        return icon;
    }

    private Icon createSpeakerIcon(boolean enabled) {
        var icon = enabled ? VaadinIcon.VOLUME_UP.create() : VaadinIcon.VOLUME_OFF.create();
        if (!enabled) {
            icon.setColor("var(--lumo-disabled-text-color)");
        }
        return icon;
    }

    private void checkBrowserSupport() {
        getElement().executeJs(
            "return { recognition: window.isSpeechRecognitionSupported(), synthesis: window.isSpeechSynthesisSupported() }"
        ).then(result -> {
            // If we got here, JS executed successfully - APIs are likely available
            logger.info("Voice APIs available");
        });
    }

    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        getElement().executeJs("window.startListening($0)", getElement());
    }

    private void stopListening() {
        getElement().executeJs("window.stopListening()");
    }

    private void toggleAutoSpeak() {
        autoSpeak = !autoSpeak;
        speakerButton.setIcon(createSpeakerIcon(autoSpeak));
        speakerButton.getElement().setAttribute("title",
            autoSpeak ? "Voice responses on - click to mute" : "Voice responses off - click to enable");

        if (!autoSpeak && isSpeaking) {
            stopSpeaking();
        }
    }

    /**
     * Speak the given text if auto-speak is enabled.
     */
    public void speak(String text) {
        if (autoSpeak && text != null && !text.isBlank()) {
            getElement().executeJs("window.speakText($0, $1)", text, getElement());
        }
    }

    /**
     * Stop any ongoing speech.
     */
    public void stopSpeaking() {
        getElement().executeJs("window.stopSpeaking()");
    }

    /**
     * Set the callback for when speech is recognized.
     */
    public void setOnSpeechRecognized(Consumer<String> callback) {
        this.onSpeechRecognized = callback;
    }

    /**
     * Check if auto-speak is enabled.
     */
    public boolean isAutoSpeakEnabled() {
        return autoSpeak;
    }

    /**
     * Set the initial auto-speak state (e.g., from user preferences).
     */
    public void setAutoSpeak(boolean enabled) {
        this.autoSpeak = enabled;
        speakerButton.setIcon(createSpeakerIcon(enabled));
        speakerButton.getElement().setAttribute("title",
            enabled ? "Voice responses on - click to mute" : "Voice responses off - click to enable");
    }

    // Callbacks from JavaScript

    @ClientCallable
    public void onListeningStarted() {
        isListening = true;
        micButton.setIcon(createMicIcon(true));
        micButton.getElement().setAttribute("title", "Listening... click to stop");
        logger.debug("Voice: listening started");
    }

    @ClientCallable
    public void onListeningStopped() {
        isListening = false;
        micButton.setIcon(createMicIcon(false));
        micButton.getElement().setAttribute("title", "Click to speak (voice input)");
        logger.debug("Voice: listening stopped");
    }

    @ClientCallable
    public void onSpeechResult(String transcript, boolean isFinal) {
        logger.debug("Voice: speech result - final={}, text={}", isFinal, transcript);
        if (isFinal && onSpeechRecognized != null) {
            onSpeechRecognized.accept(transcript);
        }
    }

    @ClientCallable
    public void onSpeechError(String error) {
        logger.warn("Voice: speech error - {}", error);
        isListening = false;
        micButton.setIcon(createMicIcon(false));

        // Show user-friendly error messages
        String message = switch (error) {
            case "not-allowed" -> "Microphone access denied. Please allow microphone access.";
            case "no-speech" -> "No speech detected. Try again.";
            case "network" -> "Network error. Check your connection.";
            default -> "Voice input error: " + error;
        };

        com.vaadin.flow.component.notification.Notification.show(
            message, 3000,
            com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER
        );
    }

    @ClientCallable
    public void onSpeakingStarted() {
        isSpeaking = true;
        logger.debug("Voice: speaking started");
    }

    @ClientCallable
    public void onSpeakingStopped() {
        isSpeaking = false;
        logger.debug("Voice: speaking stopped");
    }
}
