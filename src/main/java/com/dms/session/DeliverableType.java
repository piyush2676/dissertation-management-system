package com.dms.session;

/**
 * The documents section 2.2.3 of the guidelines requires, minus the two research
 * papers, which are outcomes rather than uploads. A review slot collects at most
 * one of these; the readiness checklist reads the slot's approval as the evidence.
 */
public enum DeliverableType {
    SYNOPSIS("Dissertation synopsis"),
    LITERATURE_SURVEY("Literature survey report"),
    SYSTEM_DESIGN("System design document"),
    TECHNICAL_REPORT("Technical report"),
    FINAL_THESIS("Final dissertation report");

    private final String label;

    DeliverableType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
