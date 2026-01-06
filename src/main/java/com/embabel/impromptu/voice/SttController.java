package com.embabel.impromptu.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for speech-to-text functionality.
 */
@RestController
@RequestMapping("/api/stt")
public class SttController {

    private static final Logger logger = LoggerFactory.getLogger(SttController.class);

    private final SpeechToTextService sttService;

    public SttController(SpeechToTextService sttService) {
        this.sttService = sttService;
    }

    /**
     * Check if STT is available.
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(new StatusResponse(sttService.isConfigured()));
    }

    /**
     * Transcribe audio to text.
     * Accepts audio file upload.
     */
    @PostMapping("/transcribe")
    public ResponseEntity<TranscribeResponse> transcribe(@RequestParam("audio") MultipartFile audioFile) {
        logger.info("STT transcribe request received, size: {} bytes, type: {}",
                audioFile.getSize(), audioFile.getContentType());

        if (audioFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new TranscribeResponse(null, "No audio file provided"));
        }

        if (!sttService.isConfigured()) {
            logger.warn("STT requested but not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new TranscribeResponse(null, "Speech recognition not configured"));
        }

        try {
            String filename = audioFile.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = "audio.webm";
            }

            String text = sttService.transcribe(audioFile.getBytes(), filename);
            return ResponseEntity.ok(new TranscribeResponse(text, null));

        } catch (SpeechToTextService.SttException e) {
            logger.error("STT transcription failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TranscribeResponse(null, e.getMessage()));

        } catch (Exception e) {
            logger.error("STT error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TranscribeResponse(null, "Transcription failed"));
        }
    }

    public record StatusResponse(boolean available) {}

    public record TranscribeResponse(String text, String error) {}
}
