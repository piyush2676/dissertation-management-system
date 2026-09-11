package com.dms.provenance;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.common.NotFoundException;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    /** No I, O, 0 or 1 -- these get read aloud and typed in by hand. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificateRepository;
    private final AllocationRepository allocationRepository;
    private final UserRepository userRepository;
    private final ProvenanceService provenanceService;

    @Value("${dms.base-url:http://localhost:8080}")
    private String baseUrl;

    public String verifyUrl(String code) {
        return baseUrl + "/verify/" + code;
    }

    /**
     * Seals the record as it stands. Re-issuing replaces the digest, because the
     * certificate is a statement about the register at a moment, not a one-off.
     */
    @Transactional
    public Certificate issue(Long allocationId, String issuerEmail) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));

        Map<String, String> facts = provenanceService.factsFor(allocation);
        User issuer = userRepository.findByEmail(issuerEmail).orElse(null);

        Certificate certificate = certificateRepository.findByAllocation(allocation)
                .orElseGet(Certificate::new);

        if (certificate.getCode() == null) {
            certificate.setCode(newCode());
        }
        certificate.setAllocation(allocation);
        certificate.setDigest(ProvenanceService.digestOf(facts));
        certificate.setPayload(facts);
        certificate.setIssuedBy(issuer);
        certificate.setIssuedAt(Instant.now());
        certificate.setRevokedAt(null);

        log.info("certificate {} issued for allocation {}", certificate.getCode(), allocationId);
        return certificateRepository.save(certificate);
    }

    @Transactional(readOnly = true)
    public Optional<Certificate> findFor(Allocation allocation) {
        return certificateRepository.findByAllocation(allocation);
    }

    @Transactional(readOnly = true)
    public Optional<Certificate> find(String code) {
        return code == null ? Optional.empty() : certificateRepository.findByCode(code.strip().toUpperCase());
    }

    /**
     * Recomputes the digest from live data and compares it with the sealed one.
     *
     * <p>A mismatch does not mean fraud. It means the register has changed since
     * the certificate was issued, which is exactly what a reader needs to be told.
     */
    @Transactional(readOnly = true)
    public VerificationResult verify(String code) {
        Optional<Certificate> found = find(code);
        if (found.isEmpty()) {
            return VerificationResult.unknown(code);
        }

        Certificate certificate = found.get();
        if (certificate.isRevoked()) {
            return VerificationResult.revoked(certificate);
        }

        String current = provenanceService.digestFor(certificate.getAllocation());
        return current.equals(certificate.getDigest())
                ? VerificationResult.matches(certificate)
                : VerificationResult.mismatched(certificate, current);
    }

    private String newCode() {
        // Random, not sequential: a code is a public handle, and guessing the next
        // one should not hand somebody else's record over.
        StringBuilder code = new StringBuilder("NIET-");
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
