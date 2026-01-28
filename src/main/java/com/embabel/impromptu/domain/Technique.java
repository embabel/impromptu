package com.embabel.impromptu.domain;

import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("A musical technique used in composition, such as counterpoint or bitonality")
@CreationPermitted(false)
public interface Technique extends NamedEntity {
}
