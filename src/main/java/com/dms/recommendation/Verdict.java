package com.dms.recommendation;

/** Annexure-6 section 7, "tick only one of the following". */
public enum Verdict {
    ACCEPTABLE("A", "Acceptable as it is"),
    MINOR_REVISIONS("B", "Acceptable after minor technical revisions or language corrections"),
    MAJOR_REVISIONS("C", "Major technical modifications and re-evaluation"),
    REJECTED("D", "Rejected: does not meet the minimum standards");

    private final String code;
    private final String label;

    Verdict(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** A and B clear the thesis for the viva board; C and D send it back first. */
    public boolean clearsForDefence() {
        return this == ACCEPTABLE || this == MINOR_REVISIONS;
    }
}
