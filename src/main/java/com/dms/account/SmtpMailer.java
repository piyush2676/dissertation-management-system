package com.dms.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real sending, switched on by configuring spring.mail.host.
 *
 * <p>A send failure is logged rather than thrown: a mail server being down must
 * not turn "we have emailed you a link" into a stack trace, and the caller has
 * already committed the token.
 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
@RequiredArgsConstructor
@Slf4j
public class SmtpMailer implements Mailer {

    private final JavaMailSender mailSender;

    @Value("${dms.mail.from:dissertation@niet.co.in}")
    private String from;

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.debug("sent '{}' to {}", subject, to);
        } catch (RuntimeException ex) {
            log.error("could not send '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }

    @Override
    public boolean isReal() {
        return true;
    }
}
