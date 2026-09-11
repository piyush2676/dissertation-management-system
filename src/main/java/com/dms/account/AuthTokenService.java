package com.dms.account;

import com.dms.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and redeems one-time tokens.
 *
 * <p>Three rules hold everywhere this is used. The plaintext is returned exactly
 * once, at issue, and is never stored -- only its SHA-256. Issuing invalidates
 * any outstanding token of the same purpose, so an older intercepted link dies.
 * And redeeming marks the row used in the same transaction, so a token cannot be
 * spent twice even under concurrent requests.
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 256 bits. Long enough that guessing is not a strategy. */
    private static final int TOKEN_BYTES = 32;

    private final AuthTokenRepository tokenRepository;

    /** The plaintext token, which the caller must put in a link and then forget. */
    public record Issued(String plaintext, AuthToken record) {
    }

    @Transactional
    public Issued issue(User user, TokenPurpose purpose) {
        tokenRepository.invalidateOutstanding(user, purpose, Instant.now());

        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AuthToken token = new AuthToken();
        token.setUser(user);
        token.setTokenHash(hash(plaintext));
        token.setPurpose(purpose);
        token.setExpiresAt(Instant.now().plus(purpose.validFor()));
        token.setCreatedAt(Instant.now());

        return new Issued(plaintext, tokenRepository.save(token));
    }

    /**
     * Redeems a token, or returns empty if it is unknown, expired or already
     * spent. The caller cannot tell those apart, which is deliberate.
     */
    @Transactional
    public Optional<User> consume(String plaintext, TokenPurpose purpose) {
        if (plaintext == null || plaintext.isBlank()) {
            return Optional.empty();
        }

        Optional<AuthToken> found = tokenRepository.findByTokenHashAndPurpose(hash(plaintext), purpose);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        AuthToken token = found.get();
        Instant now = Instant.now();
        if (!token.isUsable(now)) {
            return Optional.empty();
        }

        token.setUsedAt(now);
        tokenRepository.save(token);
        return Optional.of(token.getUser());
    }

    /** Checks a token without spending it, so a form can be shown before it is submitted. */
    @Transactional(readOnly = true)
    public boolean isUsable(String plaintext, TokenPurpose purpose) {
        if (plaintext == null || plaintext.isBlank()) {
            return false;
        }
        return tokenRepository.findByTokenHashAndPurpose(hash(plaintext), purpose)
                .filter(token -> token.isUsable(Instant.now()))
                .isPresent();
    }

    static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
