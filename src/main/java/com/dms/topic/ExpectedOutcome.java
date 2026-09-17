package com.dms.topic;

/**
 * Annexure-2's "expected outcomes, tick all that apply". A closed set, so an enum;
 * stored on the topic as one comma-separated column through
 * {@link ExpectedOutcomesConverter} rather than a join table, because a lazy
 * collection on Topic would eventually reach a template.
 */
public enum ExpectedOutcome {

    RESEARCH_PAPER("Research paper publication"),
    PATENT("Patent filing"),
    PRODUCT("Product development"),
    STARTUP("Startup idea"),
    SIH_OR_INDUSTRY("SIH / industry collaboration"),
    SDG("Sustainable development goal");

    private final String label;

    ExpectedOutcome(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
