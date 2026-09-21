package com.dms.outcome;

/**
 * Where an outcome stands. A reported state, not a workflow: the student sets it
 * to whatever the journal or patent office last said, and there is deliberately
 * no transition map -- a paper can go from ACCEPTED back to COMMUNICATED when a
 * venue withdraws. What matters to the rules is {@link #achieved()}.
 */
public enum OutcomeStatus {
    DRAFTING("Drafting", false),
    COMMUNICATED("Communicated / under review", false),
    ACCEPTED("Accepted", true),
    PUBLISHED("Published", true),
    FILED("Filed", true),
    GRANTED("Granted", true);

    private final String label;
    private final boolean achieved;

    OutcomeStatus(String label, boolean achieved) {
        this.label = label;
        this.achieved = achieved;
    }

    public String getLabel() {
        return label;
    }

    /** Counts toward the publication requirement and the deliverable checklist. */
    public boolean achieved() {
        return achieved;
    }
}
