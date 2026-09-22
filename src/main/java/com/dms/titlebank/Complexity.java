package com.dms.titlebank;

/** Format 3's "level of complexity" column. */
public enum Complexity {
    BASIC("Basic"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced");

    private final String label;

    Complexity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
