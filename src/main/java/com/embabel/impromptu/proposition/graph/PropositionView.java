/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu.proposition.graph;

import com.embabel.dice.proposition.Proposition;
import org.drivine.annotation.Direction;
import org.drivine.annotation.GraphRelationship;
import org.drivine.annotation.GraphView;
import org.drivine.annotation.Root;

import java.util.List;
import java.util.Map;

/**
 * GraphView combining a Proposition with its entity Mentions.
 * <p>
 * This view loads a proposition as the root node and its related
 * mentions via the HAS_MENTION relationship.
 */
@GraphView
public class PropositionView {

    @Root
    private PropositionNode proposition;

    @GraphRelationship(type = "HAS_MENTION", direction = Direction.OUTGOING)
    private List<Mention> mentions;

    public PropositionView() {
    }

    public PropositionView(PropositionNode proposition, List<Mention> mentions) {
        this.proposition = proposition;
        this.mentions = mentions;
    }

    public PropositionNode getProposition() {
        return proposition;
    }

    public void setProposition(PropositionNode proposition) {
        this.proposition = proposition;
    }

    public List<Mention> getMentions() {
        return mentions;
    }

    public void setMentions(List<Mention> mentions) {
        this.mentions = mentions;
    }

    /**
     * Create a PropositionView from a Dice Proposition.
     */
    public static PropositionView fromDice(Proposition p) {
        var propNode = new PropositionNode(
                p.getId(),
                p.getContextIdValue(),  // Java-friendly accessor
                p.getText(),
                p.getConfidence(),
                p.getDecay(),
                p.getReasoning(),
                p.getGrounding(),
                p.getCreated(),
                p.getRevised(),
                p.getStatus(),
                p.getUri()
        );
        var mentionNodes = p.getMentions().stream()
                .map(Mention::fromDice)
                .toList();
        return new PropositionView(propNode, mentionNodes);
    }

    /**
     * Convert this PropositionView back to a Dice Proposition.
     */
    public Proposition toDice() {
        var diceMentions = mentions.stream()
                .map(Mention::toDice)
                .toList();
        return Proposition.create(
                proposition.getId(),
                proposition.getContextId(),  // Java-friendly factory takes String
                proposition.getText(),
                diceMentions,
                proposition.getConfidence(),
                proposition.getDecay(),
                proposition.getReasoning(),
                proposition.getGrounding(),
                proposition.getCreated(),
                proposition.getRevised(),
                proposition.getStatus(),
                Map.of(),
                proposition.getUri()
        );
    }

    @Override
    public String toString() {
        return "PropositionView{" +
                "proposition=" + proposition +
                ", mentions=" + mentions +
                '}';
    }
}