package com.dms.user;

/**
 * The institute's own mail domain, which is also the ERP sign-in domain.
 *
 * <p>Every account this system creates -- seeded demo accounts and imported
 * scholars and faculty alike -- signs in with an address on this domain. There is
 * no self-registration, so a student never arrives with a public-mail address, and
 * an address on this domain counts as confirmed on creation.
 *
 * <p>Decided 2026-09-24, replacing the placeholder {@code @college.edu}. Imported
 * addresses are derived (roll number for a scholar, first.last for faculty), so they
 * may coincide with real ERP mailboxes: with SMTP configured, mail from this system
 * can reach real people. V20 moved existing databases across.
 */
public final class InstituteMail {

    public static final String DOMAIN = "@niet.co.in";

    private InstituteMail() {
    }

    public static String address(String localPart) {
        return localPart + DOMAIN;
    }
}
