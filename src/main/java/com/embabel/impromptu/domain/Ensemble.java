package com.embabel.impromptu.domain;

import com.embabel.agent.core.CreationPermitted;
import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Musical ensemble, such as a string quartet or symphony orchestra")
@CreationPermitted(false)
public interface Ensemble extends NamedEntity {
}
