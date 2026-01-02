package com.embabel.impromptu.proposition;

import com.embabel.common.core.types.SimilarityResult;
import com.embabel.common.core.types.TextSimilaritySearchRequest;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DrivinePropositionRepository implements PropositionRepository {

    @Override
    public @NonNull String getLuceneSyntaxNotes() {
        return "fully supported";
    }

    @Override
    public @NonNull Proposition save(@NonNull Proposition proposition) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public @Nullable Proposition findById(@NonNull String id) {
        return null;
    }

    @Override
    public @NonNull List<Proposition> findByEntity(@NonNull String entityId) {
        return List.of();
    }

    @Override
    public @NonNull List<SimilarityResult<Proposition>> findSimilarWithScores(@NonNull TextSimilaritySearchRequest textSimilaritySearchRequest) {
        return List.of();
    }

    @Override
    public @NonNull List<Proposition> findByStatus(@NonNull PropositionStatus status) {
        return List.of();
    }

    @Override
    public @NonNull List<Proposition> findByGrounding(@NonNull String chunkId) {
        return List.of();
    }

    @Override
    public @NonNull List<Proposition> findAll() {
        return List.of();
    }

    @Override
    public boolean delete(@NonNull String id) {
        return false;
    }

    @Override
    public int count() {
        return 0;
    }

}
