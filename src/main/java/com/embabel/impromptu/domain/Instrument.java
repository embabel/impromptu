package com.embabel.impromptu.domain;

import com.embabel.agent.rag.model.NamedEntity;

/**
 * A musical instrument.
 */
public interface Instrument extends NamedEntity {

    String getFamily();
}
