package com.dms.allocation;

import lombok.Getter;

@Getter
public class CapacityExceededException extends RuntimeException {

    private final String supervisorName;
    private final long taken;
    private final int max;

    public CapacityExceededException(String supervisorName, long taken, int max) {
        super(supervisorName + " is already supervising " + taken + " of " + max
                + " students this session.");
        this.supervisorName = supervisorName;
        this.taken = taken;
        this.max = max;
    }
}
