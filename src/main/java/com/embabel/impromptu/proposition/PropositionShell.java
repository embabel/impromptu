package com.embabel.impromptu.proposition;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.store.InMemoryPropositionRepository;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.stream.Collectors;

/**
 * Shell commands for viewing and managing propositions extracted from conversations.
 */
@ShellComponent("Proposition commands")
public class PropositionShell {

    private final PropositionRepository propositionRepository;

    public PropositionShell(PropositionRepository propositionRepository) {
        this.propositionRepository = propositionRepository;
    }

    @ShellMethod("Show all extracted propositions")
    public String propositions(
            @ShellOption(defaultValue = "20", help = "Maximum number to show") int limit) {

        var allProps = propositionRepository.findAll();

        if (allProps.isEmpty()) {
            return "No propositions extracted yet. Start a chat session to learn facts from the conversation.";
        }

        var sb = new StringBuilder();
        sb.append("\n=== Extracted Propositions (").append(allProps.size()).append(" total) ===\n\n");

        var index = 0;
        for (var prop : allProps) {
            if (index >= limit) break;
            index++;

            var resolvedIndicator = prop.isFullyResolved() ? "✓" : "○";
            sb.append(index).append(". ").append(resolvedIndicator).append(" ").append(prop.getText()).append("\n");
            sb.append("   Confidence: ").append(String.format("%.2f", prop.getConfidence()))
                    .append(" | Decay: ").append(String.format("%.2f", prop.getDecay())).append("\n");

            if (!prop.getMentions().isEmpty()) {
                var mentionStrs = prop.getMentions().stream()
                        .map(m -> {
                            var resolved = m.getResolvedId() != null
                                    ? "→" + m.getResolvedId().substring(0, Math.min(8, m.getResolvedId().length()))
                                    : "?";
                            return m.getSpan() + ":" + m.getType() + "[" + m.getRole() + "]" + resolved;
                        })
                        .collect(Collectors.joining(", "));
                sb.append("   Entities: ").append(mentionStrs).append("\n");
            }

            if (prop.getReasoning() != null) {
                sb.append("   Reasoning: ").append(prop.getReasoning()).append("\n");
            }
            sb.append("\n");
        }

        if (allProps.size() > limit) {
            sb.append("... and ").append(allProps.size() - limit).append(" more (use --limit to see more)\n");
        }

        return sb.toString();
    }

    @ShellMethod("Search propositions by text similarity")
    public String searchPropositions(
            @ShellOption(help = "Search query") String query,
            @ShellOption(defaultValue = "5", help = "Number of results") int topK) {

        var results = propositionRepository.findSimilarWithScores(query, topK, 0.0);

        if (results.isEmpty()) {
            return "No similar propositions found for: \"" + query + "\"";
        }

        var sb = new StringBuilder();
        sb.append("\n=== Propositions similar to \"").append(query).append("\" ===\n\n");

        var index = 0;
        for (var pair : results) {
            index++;
            var prop = pair.getFirst();
            var score = pair.getSecond();
            sb.append(index).append(". ").append(prop.getText()).append("\n");
            sb.append("   Similarity: ").append(String.format("%.3f", score))
                    .append(" | Confidence: ").append(String.format("%.2f", prop.getConfidence())).append("\n\n");
        }

        return sb.toString();
    }

    @ShellMethod("Show proposition statistics")
    public String propositionStats() {
        var allProps = propositionRepository.findAll();

        if (allProps.isEmpty()) {
            return "No propositions extracted yet.";
        }

        var fullyResolved = allProps.stream().filter(Proposition::isFullyResolved).count();
        var partiallyResolved = allProps.stream()
                .filter(p -> !p.isFullyResolved() && p.getMentions().stream().anyMatch(m -> m.getResolvedId() != null))
                .count();
        var unresolved = allProps.stream()
                .filter(p -> p.getMentions().stream().allMatch(m -> m.getResolvedId() == null))
                .count();

        var avgConfidence = allProps.stream()
                .mapToDouble(Proposition::getConfidence)
                .average()
                .orElse(0.0);

        // Group by entity type
        var byEntityType = allProps.stream()
                .flatMap(p -> p.getMentions().stream())
                .collect(Collectors.groupingBy(
                        m -> m.getType(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .toList();

        // Group by resolved entities
        var byEntity = allProps.stream()
                .flatMap(p -> p.getMentions().stream()
                        .filter(m -> m.getResolvedId() != null)
                        .map(m -> new Object[]{m.getResolvedId(), m.getSpan(), p}))
                .collect(Collectors.groupingBy(
                        arr -> (String) arr[0],
                        Collectors.toList()
                ))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(10)
                .toList();

        var sb = new StringBuilder();
        sb.append("\n=== Proposition Statistics ===\n\n");
        sb.append("Total propositions: ").append(allProps.size()).append("\n");
        sb.append("Fully resolved: ").append(fullyResolved).append("\n");
        sb.append("Partially resolved: ").append(partiallyResolved).append("\n");
        sb.append("Unresolved: ").append(unresolved).append("\n");
        sb.append("Average confidence: ").append(String.format("%.2f", avgConfidence)).append("\n");

        sb.append("\n--- By Entity Type ---\n");
        for (var entry : byEntityType) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" mentions\n");
        }

        if (!byEntity.isEmpty()) {
            sb.append("\n--- Top Entities (by proposition count) ---\n");
            for (var entry : byEntity) {
                var entityId = entry.getKey();
                var props = entry.getValue();
                // Get name from first occurrence
                var name = props.isEmpty() ? entityId.substring(0, 8) : (String) props.get(0)[1];
                sb.append("  ").append(name).append(": ").append(props.size()).append(" propositions\n");
            }
        }

        return sb.toString();
    }

    @ShellMethod("Clear all propositions")
    public String clearPropositions() {
        var count = propositionRepository.count();
        if (count == 0) {
            return "No propositions to clear.";
        }

        if (propositionRepository instanceof InMemoryPropositionRepository inMemoryRepo) {
            inMemoryRepo.clear();
            return "Cleared " + count + " propositions.";
        }

        // Fallback: delete one by one
        var allIds = propositionRepository.findAll().stream().map(Proposition::getId).toList();
        for (var id : allIds) {
            propositionRepository.delete(id);
        }
        return "Cleared " + count + " propositions.";
    }

    @ShellMethod("Show what the chatbot has learned about a topic")
    public String learned(@ShellOption(help = "Topic to search for") String topic) {
        var results = propositionRepository.findSimilar(topic, 10);

        if (results.isEmpty()) {
            return "Haven't learned anything about \"" + topic + "\" yet.";
        }

        var sb = new StringBuilder();
        sb.append("\n=== What I've learned about \"").append(topic).append("\" ===\n\n");

        for (var prop : results) {
            sb.append("• ").append(prop.getText()).append("\n");
            sb.append("  (confidence: ").append(String.format("%.2f", prop.getConfidence())).append(")\n\n");
        }

        return sb.toString();
    }
}
