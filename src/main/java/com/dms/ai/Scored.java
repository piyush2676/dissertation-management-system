package com.dms.ai;

/**
 * One similarity hit. Score is cosine similarity in [-1, 1]; for the text
 * embeddings used here it is effectively [0, 1].
 */
public record Scored<T>(T value, double score) {

    /** Whole percent, for display. Similarity is never shown as a bare float. */
    public int percent() {
        return (int) Math.round(Math.max(0, score) * 100);
    }
}
