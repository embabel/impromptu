package com.embabel.impromptu.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for text-to-speech functionality.
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final Logger logger = LoggerFactory.getLogger(TtsController.class);

    private final TextToSpeechService ttsService;

    public TtsController(TextToSpeechService ttsService) {
        this.ttsService = ttsService;
    }

    /**
     * Check if TTS is available.
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(new StatusResponse(
                ttsService.isConfigured(),
                ttsService.getVoice()
        ));
    }

    /**
     * Synthesize text to speech.
     * Returns MP3 audio.
     */
    @PostMapping(value = "/synthesize", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody SynthesizeRequest request) {
        logger.info("TTS synthesize request received, text length: {}",
                request.text() != null ? request.text().length() : 0);

        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (!ttsService.isConfigured()) {
            logger.warn("TTS requested but not configured (API key missing)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            byte[] audio = ttsService.synthesize(request.text());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .contentLength(audio.length)
                    .body(audio);
        } catch (TextToSpeechService.TtsException e) {
            logger.error("TTS synthesis failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public record StatusResponse(boolean available, String voice) {}

    public record SynthesizeRequest(String text) {}
}
