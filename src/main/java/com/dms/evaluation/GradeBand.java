package com.dms.evaluation;

import java.math.BigDecimal;

/**
 * The four bands every rubric row in the guidelines is described in: Excellent,
 * Good, Satisfactory, Unsatisfactory at 81, 61 and 41 percent.
 *
 * <p>A function of percentage rather than a column, because the thresholds are
 * the same on every row of both Format 6 and Format 15. The department changes
 * marks per row in data; it changes the bands, if ever, here.
 */
public enum GradeBand {

    S("Excellent", 81),
    A("Good", 61),
    B("Satisfactory", 41),
    C("Unsatisfactory", 0);

    private final String label;
    private final int floorPercent;

    GradeBand(String label, int floorPercent) {
        this.label = label;
        this.floorPercent = floorPercent;
    }

    public String getLabel() {
        return label;
    }

    public int getFloorPercent() {
        return floorPercent;
    }

    /** Null in, null out: an unscored student has no band, not a C. */
    public static GradeBand of(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        for (GradeBand band : values()) {
            if (percent.compareTo(BigDecimal.valueOf(band.floorPercent)) >= 0) {
                return band;
            }
        }
        return C;
    }
}
