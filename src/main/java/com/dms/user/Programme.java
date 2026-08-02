package com.dms.user;

/**
 * Degree programme a student is enrolled in.
 *
 * <p>This is deliberately not a {@link Role}. Role answers "what is this account allowed
 * to do" and is what Spring Security checks; Programme answers "which course is this
 * student on". An integrated student is still a STUDENT for authorisation purposes.
 *
 * <p>Adding a value here needs no code change anywhere else. Programmes differ only in
 * their Milestone rows -- deadlines, deliverables, sequence -- which are data, not logic.
 * That is the workflow-is-data principle in docs/guide.md section 6.
 *
 * <p>Stored as a string via {@code @Enumerated(EnumType.STRING)}, so the name itself
 * lands in the column and must fit {@code VARCHAR(32)} (widened in V2). Never switch to
 * ORDINAL: it stores the position, so reordering this enum would silently reclassify
 * every existing student.
 */
public enum Programme {

    /** Four-year undergraduate degree. Dissertation in the final year. */
    BTECH,

    /** Two-year postgraduate degree. Dissertation spans both years. */
    MTECH,

    /**
     * Five-year integrated B.Tech + M.Tech dual degree.
     *
     * <p>A single continuous enrolment awarding both degrees, not two separate ones.
     * The dissertation runs longer than a B.Tech project and is examined to M.Tech
     * standard, so these students get their own milestone set in Phase 4 -- typically an
     * earlier mini-project plus the full dissertation in the final year.
     */
    BTECH_MTECH_INTEGRATED
}