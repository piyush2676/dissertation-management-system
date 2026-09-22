package com.dms.titlebank;

import com.dms.topic.ExpectedOutcome;

import java.time.Instant;

/** A banked title as a page renders it; the entity never reaches a template. */
public record BankedTitleView(
        Long id,
        String title,
        String abstractText,
        String domain,
        ExpectedOutcome expectedOutcome,
        Complexity complexity,
        BankedTitleStatus status,
        Long supervisorId,
        String supervisorName,
        String supervisorDesignation,
        String researchInterests,
        Instant createdAt) {

    public boolean open() {
        return status == BankedTitleStatus.OPEN;
    }
}
