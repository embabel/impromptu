package com.embabel.impromptu.proposition;

import com.embabel.agent.rag.model.NamedEntity;

/**
 * Domain types for music-related proposition extraction.
 * These define the entity types that the LLM will extract from conversations.
 */
public class MusicDomainTypes {

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
    interface MusicPlace extends NamedEntity {
        String getLocation();
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
}
