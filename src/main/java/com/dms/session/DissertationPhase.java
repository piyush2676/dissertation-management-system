package com.dms.session;

import com.dms.user.Programme;

import java.util.Optional;

/**
 * The two halves of a dissertation as the guidelines define them (§2.1): planning
 * and proposal in the odd semester, execution and defence in the even one.
 *
 * <p>Derived from the student's semester rather than stored anywhere, so there is
 * exactly one source of truth for "which reviews and which rubric apply to this
 * student". A semester outside the dissertation years yields nothing, and the
 * pages that need a phase show an empty track rather than the wrong one.
 */
public enum DissertationPhase {

    /** Pre-Dissertation / Dissertation-I: 3rd semester M.Tech, 9th semester integrated. */
    PRE("Pre-Dissertation"),

    /** Final Dissertation / Dissertation-II: 4th semester M.Tech, 10th semester integrated. */
    FINAL("Final Dissertation");

    private final String label;

    DissertationPhase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Which semester opens this phase for the programme -- 3 or 9 for PRE, 4 or 10 for FINAL. */
    public int semesterFor(Programme programme) {
        int base = programme == Programme.BTECH_MTECH_INTEGRATED ? 9 : 3;
        return this == PRE ? base : base + 1;
    }

    public static Optional<DissertationPhase> forSemester(Programme programme, Integer semester) {
        if (programme == null || semester == null) {
            return Optional.empty();
        }
        for (DissertationPhase phase : values()) {
            if (phase.semesterFor(programme) == semester) {
                return Optional.of(phase);
            }
        }
        return Optional.empty();
    }
}
