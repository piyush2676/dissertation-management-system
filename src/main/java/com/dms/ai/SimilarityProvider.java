package com.dms.ai;

import java.util.List;

/**
 * Interface seam at the vector store. Cosine over JSONB arrays today; a pgvector
 * implementation later swaps in without a caller changing.
 */
public interface SimilarityProvider {

    /**
     * The nearest stored embeddings of one kind to the query vector, best first.
     *
     * @param excludeRefId a record to leave out -- a topic is always most similar
     *                     to itself, which is not a useful result
     */
    List<Scored<Long>> mostSimilar(double[] query, EmbeddingKind kind, int topK, Long excludeRefId);
}
