package com.dms.account;

import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * A single-use, expiring secret tied to a user and a purpose.
 *
 * <p>Only the digest is stored. The plaintext lives in the emailed link and
 * nowhere else, so this table leaking gives an attacker nothing to present.
 */
@Entity
@Table(name = "auth_tokens")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    TokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    /** Set the moment it is redeemed. A token is never valid twice. */
    @Column(name = "used_at")
    Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
