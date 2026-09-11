package com.dms.provenance;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.audit.AuditLog;
import com.dms.audit.AuditLogRepository;
import com.dms.common.NotFoundException;
import com.dms.evaluation.Evaluation;
import com.dms.evaluation.EvaluationRepository;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionRepository;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.viva.VivaSchedule;
import com.dms.viva.VivaScheduleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rebuilds one dissertation's history from the audit trail, and reduces its
 * immutable facts to a single digest.
 *
 * <p>The digest is the part that has to be exactly reproducible: verification
 * recomputes it months later and compares. So the canonical form is built from
 * fields that cannot legitimately change after the work is finished, joined in a
 * fixed order with a separator that cannot appear inside a value. Anything
 * mutable -- a display name, a formatted date, a count of notifications -- is
 * deliberately left out, or the certificate would fail for innocent reasons.
 */
@Service
@RequiredArgsConstructor
public class ProvenanceService {

    private static final String SEP = "";

    private final AllocationRepository allocationRepository;
    private final AuditLogRepository auditLogRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final EvaluationRepository evaluationRepository;
    private final VivaScheduleRepository vivaRepository;
    private final CertificateRepository certificateRepository;

    @Transactional(readOnly = true)
    public ProvenanceTimeline timelineFor(Long allocationId) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));

        List<Submission> submissions =
                submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation);

        List<ProvenanceTimeline.Entry> entries = new ArrayList<>();
        collect(entries, "Allocation", allocation.getId());
        if (allocation.getTopic() != null) {
            collect(entries, "Topic", allocation.getTopic().getId());
        }
        for (Submission submission : submissions) {
            collect(entries, "Submission", submission.getId());
        }
        entries.sort(Comparator.comparing(ProvenanceTimeline.Entry::at));

        List<ProvenanceTimeline.VersionFact> versions = new ArrayList<>();
        for (Submission submission : submissions) {
            versionRepository.findBySubmissionOrderByVersionNoDesc(submission).stream()
                    .sorted(Comparator.comparingInt(SubmissionVersion::getVersionNo))
                    .forEach(v -> versions.add(new ProvenanceTimeline.VersionFact(
                            submission.getMilestone().getName(),
                            v.getVersionNo(),
                            v.getSha256(),
                            v.getSizeBytes(),
                            v.getSubmittedAt())));
        }

        Optional<Certificate> certificate = certificateRepository.findByAllocation(allocation);

        return new ProvenanceTimeline(
                allocation.getId(),
                allocation.getStudent().getRollNo(),
                allocation.getStudent().getUser().getFullName(),
                allocation.getStudent().getProgramme().name().replace('_', ' '),
                allocation.getSession().getLabel(),
                allocation.getSupervisor().getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                entries,
                versions,
                digestFor(allocation),
                certificate.map(Certificate::getCode).orElse(null));
    }

    private void collect(List<ProvenanceTimeline.Entry> into, String entityType, Long entityId) {
        for (AuditLog row : auditLogRepository.findByEntityTypeAndEntityIdOrderByAtDesc(entityType, entityId)) {
            into.add(new ProvenanceTimeline.Entry(
                    row.getAt(),
                    row.getActorEmail(),
                    row.getAction(),
                    detail(row)));
        }
    }

    private static String detail(AuditLog row) {
        if (row.getOldValue() != null && row.getNewValue() != null) {
            return row.getOldValue() + " → " + row.getNewValue();
        }
        return row.getNewValue() == null ? "" : row.getNewValue();
    }

    // ---- digest -------------------------------------------------------------

    /** The facts a certificate seals, in a fixed order, for display and for hashing. */
    @Transactional(readOnly = true)
    public Map<String, String> factsFor(Allocation allocation) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("rollNo", allocation.getStudent().getRollNo());
        facts.put("student", allocation.getStudent().getUser().getFullName());
        facts.put("programme", allocation.getStudent().getProgramme().name());
        facts.put("session", allocation.getSession().getLabel());
        facts.put("guide", allocation.getSupervisor().getUser().getFullName());
        facts.put("topic", allocation.getTopic() == null ? "" : allocation.getTopic().getTitle());

        // Every filed version, in order, pinned by its own digest. This is what
        // makes the certificate cover the work and not merely the marks.
        List<String> shas = new ArrayList<>();
        for (Submission submission : submissionRepository
                .findByAllocationOrderByMilestoneSequenceNoAsc(allocation)) {
            versionRepository.findBySubmissionOrderByVersionNoDesc(submission).stream()
                    .sorted(Comparator.comparingInt(SubmissionVersion::getVersionNo))
                    .forEach(v -> shas.add(submission.getMilestone().getName()
                            + " v" + v.getVersionNo() + " " + v.getSha256()));
        }
        facts.put("versions", String.join(" | ", shas));

        List<Evaluation> evaluations = evaluationRepository.findByAllocation(allocation);
        evaluations.sort(Comparator.comparing(e -> e.getExaminer().getEmail()));
        facts.put("marks", evaluations.stream()
                .map(e -> e.getExaminer().getEmail() + "=" + e.getTotal().toPlainString())
                .reduce((a, b) -> a + " | " + b)
                .orElse(""));

        VivaSchedule viva = vivaRepository.findByAllocation(allocation).orElse(null);
        facts.put("viva", viva == null ? "" : viva.getStatus().name() + " " + viva.getScheduledAt());

        return facts;
    }

    @Transactional(readOnly = true)
    public String digestFor(Allocation allocation) {
        return digestOf(factsFor(allocation));
    }

    /**
     * Joined with a unit separator, which cannot occur in any of these values, so
     * two different fact sets can never produce the same canonical string.
     */
    public static String digestOf(Map<String, String> facts) {
        StringBuilder canonical = new StringBuilder();
        facts.forEach((key, value) -> canonical.append(key).append('=')
                .append(value == null ? "" : value).append(SEP));

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    sha.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
