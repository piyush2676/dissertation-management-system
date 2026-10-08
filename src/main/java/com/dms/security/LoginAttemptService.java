package com.dms.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing. Five wrong passwords for one address inside fifteen minutes lock
 * that address for fifteen minutes, and a correct password does not get through while it is
 * locked. Unknown addresses are counted the same way, so the lock cannot be used to find out
 * which addresses exist.
 *
 * <p>Held in memory, which is right for a single instance. Behind several instances each would
 * count separately, which only makes the limit looser, never stricter; move it to a table if
 * that ever matters. The cost of this design is that anyone can lock a known address for a
 * while; that is a deliberate trade against unlimited guessing, and it expires on its own.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final int PURGE_ABOVE = 5_000;

    private record Entry(int failures, Instant windowStart, Instant lockedUntil) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Entry e = entries.get(key(username));
        return e != null && e.lockedUntil() != null && e.lockedUntil().isAfter(clock.instant());
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (entries.size() > PURGE_ABOVE) {
            entries.values().removeIf(e -> expired(e, now));
        }
        entries.compute(key(username), (k, old) -> {
            if (old == null || expired(old, now)) {
                return new Entry(1, now, null);
            }
            int failures = old.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK) : old.lockedUntil();
            return new Entry(failures, old.windowStart(), lockedUntil);
        });
    }

    public void recordSuccess(String username) {
        entries.remove(key(username));
    }

    @EventListener
    void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        recordFailure(event.getAuthentication().getName());
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        recordSuccess(event.getAuthentication().getName());
    }

    private boolean expired(Entry e, Instant now) {
        boolean lockOver = e.lockedUntil() == null || !e.lockedUntil().isAfter(now);
        return lockOver && e.windowStart().plus(WINDOW).isBefore(now);
    }

    private static String key(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
