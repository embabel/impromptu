package com.embabel.impromptu.youtube;

/**
 * Exception for YouTube API errors.
 */
public class YouTubeException extends RuntimeException {

    public YouTubeException(String message) {
        super(message);
    }

    public YouTubeException(String message, Throwable cause) {
        super(message, cause);
    }
}
