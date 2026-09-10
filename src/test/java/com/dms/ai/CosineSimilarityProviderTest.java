package com.dms.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosineSimilarityProviderTest {

    @Mock private EmbeddingRepository embeddingRepository;

    @InjectMocks private CosineSimilarityProvider provider;

    @Test
    void anIdenticalVectorScoresOne() {
        stub(embedding(1L, 1, 0, 0));

        List<Scored<Long>> hits = provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 5, null);

        assertEquals(1, hits.size());
        assertEquals(1.0, hits.get(0).score(), 1e-9);
        assertEquals(100, hits.get(0).percent());
    }

    @Test
    void anOrthogonalVectorScoresZero() {
        stub(embedding(1L, 0, 1, 0));

        assertEquals(0.0, provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 5, null)
                .get(0).score(), 1e-9);
    }

    @Test
    void magnitudeDoesNotMatterOnlyDirection() {
        stub(embedding(1L, 5, 5, 0));

        assertEquals(1.0, provider.mostSimilar(new double[]{1, 1, 0}, EmbeddingKind.TOPIC, 5, null)
                .get(0).score(), 1e-9);
    }

    @Test
    void resultsComeBackBestFirst() {
        stub(embedding(1L, 0, 1, 0), embedding(2L, 1, 1, 0), embedding(3L, 1, 0, 0));

        List<Scored<Long>> hits = provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 5, null);

        assertEquals(List.of(3L, 2L, 1L), hits.stream().map(Scored::value).toList());
        assertTrue(hits.get(0).score() >= hits.get(1).score());
        assertTrue(hits.get(1).score() >= hits.get(2).score());
    }

    @Test
    void topKTrimsTheTail() {
        stub(embedding(1L, 1, 0, 0), embedding(2L, 1, 1, 0), embedding(3L, 0, 1, 0));

        assertEquals(2, provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 2, null).size());
    }

    @Test
    void theExcludedRecordIsLeftOut() {
        stub(embedding(1L, 1, 0, 0), embedding(2L, 1, 1, 0));

        List<Scored<Long>> hits = provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 5, 1L);

        assertEquals(List.of(2L), hits.stream().map(Scored::value).toList(),
                "a topic is always closest to itself, which is not a useful result");
    }

    @Test
    void aVectorFromAnotherModelIsSkippedRatherThanScored() {
        stub(embedding(1L, 1, 0), embedding(2L, 1, 0, 0));

        List<Scored<Long>> hits = provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 5, null);

        assertEquals(List.of(2L), hits.stream().map(Scored::value).toList(),
                "comparing different dimensions would give a confident meaningless number");
    }

    @Test
    void aZeroVectorIsSkippedRatherThanDividedBy() {
        stub(embedding(1L, 0, 0, 0), embedding(2L, 1, 0, 0));

        List<Scored<Long>> hits = provider.mostSimilar(new double[]{1, 0, 0}, EmbeddingKind.TOPIC, 5, null);

        assertEquals(List.of(2L), hits.stream().map(Scored::value).toList());
    }

    @Test
    void anEmptyQueryReturnsNothingWithoutTouchingTheDatabase() {
        assertTrue(provider.mostSimilar(new double[0], EmbeddingKind.TOPIC, 5, null).isEmpty());
        assertTrue(provider.mostSimilar(null, EmbeddingKind.TOPIC, 5, null).isEmpty());
    }

    @Test
    void negativePercentIsClampedToZero() {
        assertEquals(0, new Scored<>(1L, -0.4).percent(),
                "an opposed vector must not render as a negative percentage");
    }

    private void stub(Embedding... rows) {
        when(embeddingRepository.findByKind(EmbeddingKind.TOPIC)).thenReturn(List.of(rows));
    }

    private Embedding embedding(Long refId, double... values) {
        Embedding embedding = new Embedding();
        embedding.setKind(EmbeddingKind.TOPIC);
        embedding.setRefId(refId);
        embedding.setModel("test");
        embedding.setDimensions(values.length);
        embedding.setVector(java.util.Arrays.stream(values).boxed().toList());
        embedding.setSourceHash("hash");
        return embedding;
    }
}
