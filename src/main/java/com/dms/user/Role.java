package com.dms.user;

public enum Role {
    STUDENT,SUPERVISOR,ADMIN,REVIEWER,COORDINATOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
