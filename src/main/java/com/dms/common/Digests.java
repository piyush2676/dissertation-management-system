package com.dms.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * The one way this system turns a set of facts into a hex digest, shared by the
 * certificate and by anything else that seals a row. Two callers hashing the same
 * facts the same way is the property verification depends on, so there is exactly
 * one implementation.
 */
public final class Digests {

    /** Unit separator: cannot occur in any value, so distinct fact sets never collide. */
    private static final char SEP = '';

    private Digests() {
    }

    /**
     * Facts joined as {@code key=value} in the map's iteration order. Callers pass a
     * LinkedHashMap; the order is part of the canonical form.
     */
    public static String sha256Of(Map<String, String> facts) {
        StringBuilder canonical = new StringBuilder();
        facts.forEach((key, value) -> canonical.append(key).append('=')
                .append(value == null ? "" : value).append(SEP));
        return sha256Of(canonical.toString());
    }

    public static String sha256Of(String canonical) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
