package com.dms.recommendation;

import java.time.Instant;

/** Annexure-6 as a page renders it. A view record; the entity never reaches a template. */
public record RecommendationView(
        Long allocationId,
        String rollNo,
        String studentName,
        String thesisCode,
        String topicTitle,
        String supervisorName,
        Verdict verdict,
        String organisation,
        String technicalContent,
        String strengths,
        String queries,
        String vivaQuestions,
        String submittedByName,
        Instant submittedAt,
        Instant updatedAt) {

    public boolean revised() {
        return updatedAt != null && submittedAt != null && updatedAt.isAfter(submittedAt.plusSeconds(1));
    }
}
