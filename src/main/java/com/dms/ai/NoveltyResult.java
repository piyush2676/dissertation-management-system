package com.dms.ai;

import java.util.List;

/**
 * What the novelty check found. The narrative half may be absent -- the
 * similarity result stands on its own without a chat model.
 */
public record NoveltyResult(
        Long topicId,
        String title,
        List<Neighbour> neighbours,
        String narrative,
        String model) {

    public record Neighbour(Long topicId, String title, double score) {
        public int percent() {
            return (int) Math.round(Math.max(0, score) * 100);
        }

        /** Bands for display. Deliberately coarse -- a raw float invites false precision. */
        public String band() {
            int p = percent();
            if (p >= 85) {
                return "very close";
            }
            if (p >= 70) {
                return "close";
            }
            if (p >= 55) {
                return "related";
            }
            return "distant";
        }
    }

    public boolean hasNeighbours() {
        return !neighbours.isEmpty();
    }

    public boolean hasNarrative() {
        return narrative != null && !narrative.isBlank();
    }

    /** The closest match, which is the number a reader actually looks for. */
    public int closestPercent() {
        return neighbours.isEmpty() ? 0 : neighbours.get(0).percent();
    }
}
