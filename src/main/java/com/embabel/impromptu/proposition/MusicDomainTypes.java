package com.embabel.impromptu.proposition;

import java.util.List;

/**
 * Domain types for music-related proposition extraction.
 * These define the entity types that the LLM will extract from conversations.
 */
public class MusicDomainTypes {

    /**
     * A composer or musician.
     */
    public record Composer(
            String name,
            String nationality,
            String era,
            List<String> instruments
    ) {
        public Composer(String name) {
            this(name, null, null, List.of());
        }
    }

    /**
     * A musical work (symphony, sonata, opera, etc.).
     */
    public record MusicalWork(
            String title,
            Composer composer,
            String genre,
            Integer year
    ) {
        public MusicalWork(String title) {
            this(title, null, null, null);
        }
    }

    /**
     * A music critic or writer about music.
     */
    public record Critic(
            String name,
            String publication
    ) {
        public Critic(String name) {
            this(name, null);
        }
    }

    /**
     * A musical genre or style.
     */
    public record Genre(
            String name,
            String era
    ) {
        public Genre(String name) {
            this(name, null);
        }
    }

    /**
     * A musical instrument.
     */
    public record Instrument(
            String name,
            String family
    ) {
        public Instrument(String name) {
            this(name, null);
        }
    }

    /**
     * A place relevant to music history.
     */
    public record MusicPlace(
            String name,
            String significance
    ) {
        public MusicPlace(String name) {
            this(name, null);
        }
    }

    /**
     * A musical concept or term.
     */
    public record MusicalConcept(
            String name,
            String definition
    ) {
        public MusicalConcept(String name) {
            this(name, null);
        }
    }

    /**
     * The user interacting with the chatbot - for user modeling.
     */
    public record ChatUser(
            String identifier,
            List<String> interests
    ) {
        public ChatUser(String identifier) {
            this(identifier, List.of());
        }
    }
}
