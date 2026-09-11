package com.dms.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The fallback when no mail server is configured: the message goes to the log,
 * link and all.
 *
 * <p>This is how the flow demonstrates without credentials. It is emphatically
 * not for a real deployment -- anyone who can read the log can take over an
 * account, which is why the banner on screen says the address was not written to.
 */
@Component
@ConditionalOnMissingBean(SmtpMailer.class)
@Primary
@Slf4j
public class LoggingMailer implements Mailer {

    @Override
    public void send(String to, String subject, String body) {
        log.warn("""
                        No mail server configured, so nothing was sent. The message was:
                        ------------------------------------------------------------
                        To      : {}
                        Subject : {}

                        {}
                        ------------------------------------------------------------""",
                to, subject, body);
    }

    @Override
    public boolean isReal() {
        return false;
    }
}
