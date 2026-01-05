package com.embabel.impromptu.domain;

import com.embabel.agent.rag.model.NamedEntity;

/**
 * A place relevant to music history.
 */
public interface MusicPlace extends NamedEntity {
    String getLocation();
}
