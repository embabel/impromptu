package com.embabel.impromptu.domain;

import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * A musical instrument.
 */
@JsonClassDescription("Family of musical instruments, such as strings or brass")
@CreationPermitted(false)
public interface Family extends NamedEntity {

}
