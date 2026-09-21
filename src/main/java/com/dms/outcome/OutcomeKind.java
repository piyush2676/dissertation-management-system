package com.dms.outcome;

/** What the dissertation produced, in the categories the guidelines count (section 4.4). */
public enum OutcomeKind {
    JOURNAL_PAPER("Journal paper"),
    CONFERENCE_PAPER("Conference paper"),
    PATENT("Patent"),
    PRODUCT("Product or prototype"),
    OTHER("Other scholarly outcome");

    private final String label;

    OutcomeKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPaper() {
        return this == JOURNAL_PAPER || this == CONFERENCE_PAPER;
    }
}
