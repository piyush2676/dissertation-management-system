package com.dms.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact cosine scan over the stored vectors.
 *
 * <p>Exact rather than approximate on purpose: a department session holds tens of
 * topics, where a full scan is faster than building an index and is exactly right
 * rather than nearly right. When the archive grows past a few thousand rows this
 * is the class pgvector replaces.
 */
@Component
@RequiredArgsConstructor
public class CosineSimilarityProvider implements SimilarityProvider {

    private final EmbeddingRepository embeddingRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Scored<Long>> mostSimilar(double[] query, EmbeddingKind kind, int topK, Long excludeRefId) {
        if (query == null || query.length == 0 || topK <= 0) {
            return List.of();
        }

        double queryNorm = norm(query);
        if (queryNorm == 0) {
            return List.of();
        }

        List<Scored<Long>> scored = new ArrayList<>();
        for (Embedding candidate : embeddingRepository.findByKind(kind)) {
            if (excludeRefId != null && excludeRefId.equals(candidate.getRefId())) {
                continue;
            }
            double[] vector = candidate.toArray();
            if (vector.length != query.length) {
                // A vector embedded by a different model. Comparing them would
                // produce a confident, meaningless number, so skip it.
                continue;
            }
            double candidateNorm = norm(vector);
            if (candidateNorm == 0) {
                continue;
            }
            scored.add(new Scored<>(candidate.getRefId(), dot(query, vector) / (queryNorm * candidateNorm)));
        }

        scored.sort(Comparator.comparingDouble(Scored<Long>::score).reversed());
        return scored.size() <= topK ? scored : new ArrayList<>(scored.subList(0, topK));
    }

    static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    static double norm(double[] v) {
        return Math.sqrt(dot(v, v));
    }
}
