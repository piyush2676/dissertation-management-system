package com.dms.provenance;

import java.time.Instant;
import java.util.Map;

/**
 * The answer a verify page gives.
 *
 * <p>Four outcomes, and the wording of each matters. A mismatch is not an
 * accusation -- it means the register has moved since the certificate was
 * issued, which is what the reader needs to know without being told what to
 * conclude from it.
 */
public record VerificationResult(
        Status status,
        String code,
        Map<String, String> claimed,
        String sealedDigest,
        String currentDigest,
        Instant issuedAt,
        String issuedBy) {

    public enum Status {
        /** The record still matches what was sealed. */
        MATCHES,
        /** The record has changed since issue. */
        CHANGED,
        /** Withdrawn by the department. */
        REVOKED,
        /** No such certificate. */
        UNKNOWN
    }

    public boolean isMatch() {
        return status == Status.MATCHES;
    }

    public boolean isKnown() {
        return status != Status.UNKNOWN;
    }

    static VerificationResult unknown(String code) {
        return new VerificationResult(Status.UNKNOWN, code, Map.of(), null, null, null, null);
    }

    static VerificationResult matches(Certificate certificate) {
        return of(Status.MATCHES, certificate, certificate.getDigest());
    }

    static VerificationResult mismatched(Certificate certificate, String current) {
        return of(Status.CHANGED, certificate, current);
    }

    static VerificationResult revoked(Certificate certificate) {
        return of(Status.REVOKED, certificate, null);
    }

    private static VerificationResult of(Status status, Certificate certificate, String current) {
        return new VerificationResult(
                status,
                certificate.getCode(),
                certificate.getPayload(),
                certificate.getDigest(),
                current,
                certificate.getIssuedAt(),
                certificate.getIssuedBy() == null ? null : certificate.getIssuedBy().getFullName());
    }
}
