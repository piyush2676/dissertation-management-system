package com.dms.change;

/** What section 4.11 lets a scholar ask to change. */
public enum ChangeKind {
    SUPERVISOR("Change of supervisor"),
    TITLE("Change of thesis title");

    private final String label;

    ChangeKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
