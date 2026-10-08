package com.dms.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    /** A clock the test can move. */
    private static final class Moving extends Clock {
        private Instant now = Instant.parse("2026-10-08T09:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final Moving clock = new Moving();
    private final LoginAttemptService attempts = new LoginAttemptService(clock);

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(user);
        }
    }

    @Test
    void fourFailuresDoNotLock() {
        fail("a@niet.co.in", 4);
        assertThat(attempts.isLocked("a@niet.co.in")).isFalse();
    }

    @Test
    void theFifthFailureLocks() {
        fail("a@niet.co.in", 5);
        assertThat(attempts.isLocked("a@niet.co.in")).isTrue();
    }

    @Test
    void theLockLiftsAfterFifteenMinutes() {
        fail("a@niet.co.in", 5);
        clock.advance(Duration.ofMinutes(14));
        assertThat(attempts.isLocked("a@niet.co.in")).isTrue();
        clock.advance(Duration.ofMinutes(2));
        assertThat(attempts.isLocked("a@niet.co.in")).isFalse();
    }

    @Test
    void addressesAreCaseAndSpaceInsensitive() {
        fail("A@Niet.co.in ", 5);
        assertThat(attempts.isLocked("a@niet.co.in")).isTrue();
    }

    @Test
    void oneAddressLockingDoesNotLockAnother() {
        fail("a@niet.co.in", 5);
        assertThat(attempts.isLocked("b@niet.co.in")).isFalse();
    }

    @Test
    void failuresOutsideTheWindowDoNotAddUp() {
        fail("a@niet.co.in", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("a@niet.co.in", 4);
        assertThat(attempts.isLocked("a@niet.co.in")).isFalse();
    }

    @Test
    void aSuccessfulSignInClearsTheCount() {
        fail("a@niet.co.in", 4);
        attempts.recordSuccess("a@niet.co.in");
        fail("a@niet.co.in", 4);
        assertThat(attempts.isLocked("a@niet.co.in")).isFalse();
    }

    @Test
    void unknownAddressesAreCountedLikeKnownOnes() {
        fail("nobody@niet.co.in", 5);
        assertThat(attempts.isLocked("nobody@niet.co.in")).isTrue();
    }

    @Test
    void aNullUsernameDoesNotBlowUp() {
        attempts.recordFailure(null);
        assertThat(attempts.isLocked(null)).isFalse();
    }
}
