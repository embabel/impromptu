package com.embabel.impromptu.voice;

import com.embabel.impromptu.ImpromptuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Speech-to-text service using OpenAI's Whisper API.
 * Provides high-quality transcription from audio.
 */
@Service
public class SpeechToTextService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechToTextService.class);
    private static final String OPENAI_TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions";

    private final RestClient restClient = RestClient.create();

    private final ImpromptuProperties properties;

    public SpeechToTextService(ImpromptuProperties properties) {
        this.properties = properties;
    }

    /**
     * Transcribe audio to text.
     *
     * @param audioData Audio data (webm, mp3, wav, etc.)
     * @param filename  Original filename with extension
     * @return Transcribed text
     * @throws SttException if transcription fails
     */
    public String transcribe(byte[] audioData, String filename) {
        if (!properties.isSpeechConfigured()) {
            throw new SttException("OpenAI API key not configured");
        }

        if (audioData == null || audioData.length == 0) {
            throw new SttException("Audio data cannot be empty");
        }

        try {
            logger.info("Transcribing audio: {} bytes, filename: {}", audioData.length, filename);

            // Build multipart form data manually
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            StringBuilder body = new StringBuilder();

            // Add model field
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            body.append(properties.speech().ttsModel()).append("\r\n");

            // Add file field header
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"\r\n");
            body.append("Content-Type: audio/webm\r\n\r\n");

            // Combine parts with binary audio data
            byte[] headerBytes = body.toString().getBytes();
            byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes();

            byte[] fullBody = new byte[headerBytes.length + audioData.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, fullBody, 0, headerBytes.length);
            System.arraycopy(audioData, 0, fullBody, headerBytes.length, audioData.length);
            System.arraycopy(footerBytes, 0, fullBody, headerBytes.length + audioData.length, footerBytes.length);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(OPENAI_TRANSCRIPTION_URL)
                    .header("Authorization", "Bearer " + properties.speech().apiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .body(fullBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new SttException("Empty response from OpenAI");
            }

            String text = (String) response.get("text");
            logger.info("Transcribed: \"{}\"", text);
            return text != null ? text.trim() : "";

        } catch (SttException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Transcription failed: {}", e.getMessage());
            throw new SttException("Transcription failed: " + e.getMessage(), e);
        }
    }

    public boolean isConfigured() {
        return properties.isSpeechConfigured();
    }

    public static class SttException extends RuntimeException {
        public SttException(String message) {
            super(message);
        }

        public SttException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
