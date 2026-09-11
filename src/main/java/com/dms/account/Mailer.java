package com.dms.account;

/**
 * Interface seam at the mail server -- the same pattern as StorageService.
 *
 * <p>The default implementation writes the message to the log instead of sending
 * it, so a checkout with no SMTP credentials still runs the whole flow. Real
 * sending switches on by configuring spring.mail.host.
 */
public interface Mailer {

    void send(String to, String subject, String body);

    /** Whether mail actually leaves the building, for honest wording on screen. */
    boolean isReal();
}
