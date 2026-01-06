package com.embabel.impromptu.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Text-to-speech service using OpenAI's TTS API.
 * Produces natural-sounding speech from text.
 */
@Service
public class TextToSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechService.class);
    private static final String OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech";

    private final RestClient restClient = RestClient.create();

    @Value("${impromptu.voice.openai-api-key:}")
    private String apiKey;

    @Value("${impromptu.voice.tts-model:tts-1}")
    private String model;

    @Value("${impromptu.voice.tts-voice:nova}")
    private String voice;

    /**
     * Available voices: alloy, echo, fable, onyx, nova, shimmer
     * - alloy: neutral
     * - echo: male
     * - fable: British accent
     * - onyx: deep male
     * - nova: female (recommended)
     * - shimmer: soft female
     */

    /**
     * Check if TTS is configured (API key available).
     */
    public boolean isConfigured() {
        boolean configured = apiKey != null && !apiKey.isEmpty();
        logger.debug("TTS configured: {} (key length: {})", configured,
                apiKey != null ? apiKey.length() : 0);
        return configured;
    }

    /**
     * Convert text to speech, returning MP3 audio bytes.
     *
     * @param text The text to convert to speech
     * @return MP3 audio data
     * @throws TtsException if the conversion fails
     */
    public byte[] synthesize(String text) {
        if (!isConfigured()) {
            throw new TtsException("OpenAI API key not configured");
        }

        if (text == null || text.isBlank()) {
            throw new TtsException("Text cannot be empty");
        }

        // OpenAI TTS has a 4096 character limit
        String truncatedText = text.length() > 4000 ? text.substring(0, 4000) + "..." : text;

        try {
            logger.debug("Synthesizing speech: {} chars, voice={}, model={}",
                    truncatedText.length(), voice, model);

            byte[] audio = restClient.post()
                    .uri(OPENAI_TTS_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "input", truncatedText,
                            "voice", voice,
                            "response_format", "mp3"
                    ))
                    .retrieve()
                    .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new TtsException("Empty audio response from OpenAI");
            }

            logger.debug("Synthesized {} bytes of audio", audio.length);
            return audio;

        } catch (Exception e) {
            logger.error("TTS synthesis failed: {}", e.getMessage());
            throw new TtsException("Speech synthesis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get the current voice setting.
     */
    public String getVoice() {
        return voice;
    }

    public static class TtsException extends RuntimeException {
        public TtsException(String message) {
            super(message);
        }

        public TtsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
