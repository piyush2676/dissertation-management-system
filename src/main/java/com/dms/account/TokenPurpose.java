package com.dms.account;

import java.time.Duration;

/** What a one-time token is for, and how long it stays usable. */
public enum TokenPurpose {

    /** Confirms the address can actually receive mail. */
    EMAIL_VERIFICATION(Duration.ofHours(48)),

    /** Short-lived on purpose: it is a bearer credential for an account. */
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration validFor;

    TokenPurpose(Duration validFor) {
        this.validFor = validFor;
    }

    public Duration validFor() {
        return validFor;
    }
}
