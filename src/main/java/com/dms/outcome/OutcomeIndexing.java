package com.dms.outcome;

/** Where a paper is indexed. The publication rule (section 4.4) turns on this. */
public enum OutcomeIndexing {
    SCI("SCI / SCIE"),
    SCOPUS("Scopus"),
    IEEE("IEEE"),
    ESCI("ESCI"),
    OTHER("Other / peer reviewed"),
    NONE("Not indexed");

    private final String label;

    OutcomeIndexing(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
