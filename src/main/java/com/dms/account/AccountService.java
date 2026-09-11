package com.dms.account;

import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Address confirmation and password reset.
 *
 * <p>Both entry points are deliberately silent about whether an address is known.
 * Telling a stranger "no such account" turns this into a way to enumerate who
 * studies here, so the caller always gets the same answer and the mail is simply
 * not sent when there is nobody to send it to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    /** Rejecting anything shorter is the floor, not a policy worth boasting about. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final AuthTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final Mailer mailer;

    @Value("${dms.base-url:http://localhost:8080}")
    private String baseUrl;

    public boolean mailIsReal() {
        return mailer.isReal();
    }

    // ---- address confirmation ----------------------------------------------

    /** Sends a confirmation link. Silent when the address is unknown or already confirmed. */
    @Transactional
    public void sendVerification(String email) {
        Optional<User> found = userRepository.findByEmail(email == null ? "" : email.strip());
        if (found.isEmpty()) {
            log.debug("verification requested for an unknown address");
            return;
        }

        User user = found.get();
        if (user.getEmailVerifiedAt() != null) {
            return;
        }

        String token = tokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION).plaintext();
        mailer.send(user.getEmail(),
                "Confirm your address for the NIET dissertation system",
                """
                %s,

                Please confirm this address so the dissertation cell can reach you about
                topic decisions, guide allocation and submission deadlines.

                %s/verify-email?token=%s

                The link is good for 48 hours. If you were not expecting this, ignore it.
                """.formatted(user.getFullName(), baseUrl, token));
    }

    /** Redeems a confirmation link. Returns the confirmed user, or empty if the token is no good. */
    @Transactional
    public Optional<User> confirmEmail(String token) {
        Optional<User> user = tokenService.consume(token, TokenPurpose.EMAIL_VERIFICATION);
        user.ifPresent(u -> {
            u.setEmailVerifiedAt(Instant.now());
            userRepository.save(u);
            log.info("address confirmed for {}", u.getEmail());
        });
        return user;
    }

    // ---- password reset -----------------------------------------------------

    /** Sends a reset link. Silent when the address is unknown, for the reason above. */
    @Transactional
    public void sendPasswordReset(String email) {
        Optional<User> found = userRepository.findByEmail(email == null ? "" : email.strip());
        if (found.isEmpty()) {
            log.debug("password reset requested for an unknown address");
            return;
        }

        User user = found.get();
        String token = tokenService.issue(user, TokenPurpose.PASSWORD_RESET).plaintext();
        mailer.send(user.getEmail(),
                "Reset your NIET dissertation system password",
                """
                %s,

                Someone asked to reset the password for this account. If that was you,
                follow the link below. If it was not, do nothing -- your password has
                not changed.

                %s/reset-password?token=%s

                The link is good for one hour and can be used once.
                """.formatted(user.getFullName(), baseUrl, token));
    }

    public boolean resetTokenLooksUsable(String token) {
        return tokenService.isUsable(token, TokenPurpose.PASSWORD_RESET);
    }

    /**
     * Sets a new password against a reset token.
     *
     * <p>Confirming the address as a side effect is deliberate: receiving the mail
     * proves the address works, which is the only thing verification establishes.
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.strip().length() < MIN_PASSWORD_LENGTH) {
            return false;
        }

        Optional<User> user = tokenService.consume(token, TokenPurpose.PASSWORD_RESET);
        if (user.isEmpty()) {
            return false;
        }

        User target = user.get();
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        if (target.getEmailVerifiedAt() == null) {
            target.setEmailVerifiedAt(Instant.now());
        }
        userRepository.save(target);
        log.info("password reset for {}", target.getEmail());
        return true;
    }
}
