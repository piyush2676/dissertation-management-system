package com.dms.submission;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.session.Milestone;
import com.dms.session.MilestoneRepository;
import com.dms.storage.StoredFile;
import com.dms.storage.StorageService;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final MilestoneRepository milestoneRepository;
    private final AllocationService allocationService;
    private final StorageService storageService;
    private final UserRepository userRepository;

    // ---- student ------------------------------------------------------------

    /**
     * Every milestone in the student's active session, with whatever they have
     * submitted against it. Milestones with no submission still appear -- the
     * register is the point of the page.
     */
    @Transactional(readOnly = true)
    public StudentSubmissionBoard boardFor(String studentEmail) {
        Optional<Allocation> maybe = allocationService.currentAllocationFor(studentEmail);

        if (maybe.isEmpty() || !maybe.get().getStatus().occupiesASeat()) {
            return new StudentSubmissionBoard(false, null, null, List.of());
        }

        Allocation allocation = maybe.get();
        List<Milestone> milestones = milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession());

        Map<Long, Submission> byMilestone = new HashMap<>();
        for (Submission submission : submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)) {
            byMilestone.put(submission.getMilestone().getId(), submission);
        }

        List<StudentSubmissionBoard.MilestoneRow> rows = new ArrayList<>();
        for (Milestone milestone : milestones) {
            Submission submission = byMilestone.get(milestone.getId());

            Instant lastSubmittedAt = null;
            if (submission != null) {
                lastSubmittedAt = versionRepository.findFirstBySubmissionOrderByVersionNoDesc(submission)
                        .map(SubmissionVersion::getSubmittedAt)
                        .orElse(null);
            }

            rows.add(new StudentSubmissionBoard.MilestoneRow(
                    milestone.getId(),
                    milestone.getName(),
                    milestone.getDescription(),
                    milestone.getDueDate(),
                    milestone.getWeightage(),
                    milestone.getSequenceNo(),
                    submission == null ? null : submission.getId(),
                    submission == null ? null : submission.getStatus(),
                    submission == null ? 0 : submission.getCurrentVersionNo(),
                    submission != null && submission.isLate(),
                    submission == null ? null : submission.getDecisionNote(),
                    lastSubmittedAt));
        }

        return new StudentSubmissionBoard(
                true,
                allocation.getSession().getLabel(),
                allocation.getSupervisor().getUser().getFullName(),
                rows);
    }

    /**
     * Adds a version to a milestone slot, creating the slot on first upload.
     *
     * <p>Versions are append-only: a resubmission never overwrites the previous
     * file, it becomes version n+1 and the earlier one stays readable.
     */
    public Submission upload(String studentEmail, Long milestoneId, MultipartFile file, String note) {
        Allocation allocation = allocationService.currentAllocationFor(studentEmail)
                .filter(a -> a.getStatus().occupiesASeat())
                .orElseThrow(() -> new IllegalStateException(
                        "You need an accepted guide before you can submit work."));

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new NotFoundException("Milestone", milestoneId));

        if (!milestone.getSession().getId().equals(allocation.getSession().getId())) {
            throw new NotFoundException("Milestone", milestoneId);
        }

        Submission submission = submissionRepository.findByAllocationAndMilestone(allocation, milestone)
                .orElseGet(() -> {
                    Submission fresh = new Submission();
                    fresh.setAllocation(allocation);
                    fresh.setMilestone(milestone);
                    fresh.setStatus(SubmissionStatus.DRAFT);
                    return submissionRepository.save(fresh);
                });

        if (!submission.getStatus().acceptsUpload()) {
            throw new InvalidStateTransitionException(submission.getStatus(), SubmissionStatus.SUBMITTED);
        }
        if (!submission.getStatus().canTransitionTo(SubmissionStatus.SUBMITTED)) {
            throw new InvalidStateTransitionException(submission.getStatus(), SubmissionStatus.SUBMITTED);
        }

        StoredFile stored = storageService.store(file, allocation.getId() + "/" + milestone.getId());

        // An identical re-upload adds nothing to the history and hides the real
        // change from the guide, so refuse it and drop the file just written.
        if (versionRepository.existsBySubmissionAndSha256(submission, stored.sha256())) {
            storageService.delete(stored.storagePath());
            throw new IllegalStateException("That is byte-for-byte the file already on record. Upload the revised document.");
        }

        int versionNo = submission.getCurrentVersionNo() + 1;

        SubmissionVersion version = new SubmissionVersion();
        version.setSubmission(submission);
        version.setVersionNo(versionNo);
        version.setStoragePath(stored.storagePath());
        version.setOriginalFilename(stored.originalFilename());
        version.setContentType(stored.contentType());
        version.setSha256(stored.sha256());
        version.setSizeBytes(stored.sizeBytes());
        version.setNote(note == null || note.isBlank() ? null : note.strip());
        version.setSubmittedAt(Instant.now());
        versionRepository.save(version);

        if (versionNo == 1) {
            // Lateness is judged on the first attempt. A revision arriving after the
            // due date is the review cycle running, not the student missing it.
            LocalDate submittedOn = version.getSubmittedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            submission.setLate(submittedOn.isAfter(milestone.getDueDate()));
        }

        submission.setCurrentVersionNo(versionNo);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setDecisionNote(null);
        submission.setDecidedBy(null);
        submission.setDecidedAt(null);
        submission.setUpdatedAt(Instant.now());

        return submissionRepository.save(submission);
    }

    // ---- guide --------------------------------------------------------------

    @Transactional(readOnly = true)
    public SupervisorSubmissionQueue queueFor(String supervisorEmail) {
        List<SupervisorSubmissionQueue.QueueRow> pending =
                submissionRepository.findByAllocationSupervisorUserEmailAndStatusInOrderByUpdatedAtAsc(
                                supervisorEmail, SubmissionStatus.AWAITING_GUIDE)
                        .stream().map(SubmissionService::queueRow).toList();

        List<SupervisorSubmissionQueue.QueueRow> decided =
                submissionRepository.findByAllocationSupervisorUserEmailOrderByUpdatedAtDesc(supervisorEmail)
                        .stream()
                        .filter(s -> !s.getStatus().awaitingGuide())
                        .map(SubmissionService::queueRow).toList();

        return new SupervisorSubmissionQueue(pending, decided);
    }

    /** SUBMITTED to UNDER_REVIEW. Records that the guide has picked the work up. */
    public Submission startReview(String supervisorEmail, Long submissionId) {
        Submission submission = loadForSupervisor(submissionId, supervisorEmail);

        if (!submission.getStatus().canTransitionTo(SubmissionStatus.UNDER_REVIEW)) {
            throw new InvalidStateTransitionException(submission.getStatus(), SubmissionStatus.UNDER_REVIEW);
        }

        submission.setStatus(SubmissionStatus.UNDER_REVIEW);
        submission.setUpdatedAt(Instant.now());
        return submissionRepository.save(submission);
    }

    /** UNDER_REVIEW to APPROVED, REVISION_REQUESTED or REJECTED. */
    public Submission decide(String supervisorEmail, Long submissionId, SubmissionStatus target, String note) {
        Submission submission = loadForSupervisor(submissionId, supervisorEmail);

        if (!submission.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(submission.getStatus(), target);
        }
        if (target != SubmissionStatus.APPROVED && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("A note is required when you do not approve the work.");
        }

        User decidedBy = userRepository.findByEmail(supervisorEmail)
                .orElseThrow(() -> new NotFoundException("User " + supervisorEmail + " not found"));

        submission.setStatus(target);
        submission.setDecisionNote(note == null || note.isBlank() ? null : note.strip());
        submission.setDecidedBy(decidedBy);
        submission.setDecidedAt(Instant.now());
        submission.setUpdatedAt(Instant.now());
        return submissionRepository.save(submission);
    }

    // ---- shared -------------------------------------------------------------

    /**
     * One slot with its whole version history. Readable by the student who owns it,
     * the guide supervising them, and the coordinator or admin.
     */
    @Transactional(readOnly = true)
    public SubmissionDetail detailFor(Long submissionId, String email, boolean privileged) {
        Submission submission = submissionRepository.findWithGraphById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission", submissionId));

        if (!privileged && !canRead(submissionId, email)) {
            // Deliberately a 404, not a 403: a stranger learns nothing about whether
            // this submission exists.
            throw new NotFoundException("Submission", submissionId);
        }

        List<SubmissionDetail.VersionRow> versions = versionRepository
                .findBySubmissionOrderByVersionNoDesc(submission).stream()
                .map(v -> new SubmissionDetail.VersionRow(
                        v.getId(),
                        v.getVersionNo(),
                        v.getOriginalFilename(),
                        v.getContentType(),
                        v.getSha256(),
                        v.getSizeBytes(),
                        v.getNote(),
                        v.getSubmittedAt()))
                .toList();

        Allocation allocation = submission.getAllocation();

        return new SubmissionDetail(
                submission.getId(),
                allocation.getStudent().getUser().getFullName(),
                allocation.getStudent().getRollNo(),
                allocation.getSupervisor().getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                submission.getMilestone().getName(),
                submission.getMilestone().getDueDate(),
                submission.getMilestone().getWeightage(),
                submission.getStatus(),
                submission.isLate(),
                submission.getDecisionNote(),
                submission.getDecidedBy() == null ? null : submission.getDecidedBy().getFullName(),
                submission.getDecidedAt(),
                versions);
    }

    /**
     * Clears one version for download. The uploads directory is not served as a
     * static resource, so this ownership check is the only way to a file.
     */
    @Transactional(readOnly = true)
    public SubmissionDownload download(Long versionId, String email, boolean privileged) {
        SubmissionVersion version = versionRepository.findWithGraphById(versionId)
                .orElseThrow(() -> new NotFoundException("Submission version", versionId));

        Long submissionId = version.getSubmission().getId();
        if (!privileged && !canRead(submissionId, email)) {
            throw new NotFoundException("Submission version", versionId);
        }

        return new SubmissionDownload(
                storageService.load(version.getStoragePath()),
                downloadName(version),
                version.getContentType(),
                version.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public boolean canRead(Long submissionId, String email) {
        return submissionRepository.existsByIdAndAllocationStudentUserEmail(submissionId, email)
                || submissionRepository.existsByIdAndAllocationSupervisorUserEmail(submissionId, email);
    }

    @Transactional(readOnly = true)
    public long pendingCountFor(String supervisorEmail) {
        return submissionRepository.countByAllocationSupervisorUserEmailAndStatusIn(
                supervisorEmail, SubmissionStatus.AWAITING_GUIDE);
    }

    // ---- helpers ------------------------------------------------------------

    private Submission loadForSupervisor(Long submissionId, String supervisorEmail) {
        Submission submission = submissionRepository.findWithGraphById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission", submissionId));

        if (!submissionRepository.existsByIdAndAllocationSupervisorUserEmail(submissionId, supervisorEmail)) {
            throw new NotFoundException("Submission", submissionId);
        }
        return submission;
    }

    private static SupervisorSubmissionQueue.QueueRow queueRow(Submission submission) {
        Allocation allocation = submission.getAllocation();
        return new SupervisorSubmissionQueue.QueueRow(
                submission.getId(),
                allocation.getStudent().getUser().getFullName(),
                allocation.getStudent().getRollNo(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                submission.getMilestone().getName(),
                submission.getMilestone().getDueDate(),
                submission.getStatus(),
                submission.getCurrentVersionNo(),
                submission.isLate(),
                submission.getUpdatedAt());
    }

    /** Names the download for a human filing it, not the opaque stored name. */
    private static String downloadName(SubmissionVersion version) {
        Submission submission = version.getSubmission();
        String milestone = submission.getMilestone().getName().replaceAll("[^A-Za-z0-9]+", "-");
        String roll = submission.getAllocation().getStudent().getRollNo();
        String extension = version.getOriginalFilename().contains(".")
                ? version.getOriginalFilename().substring(version.getOriginalFilename().lastIndexOf('.'))
                : "";
        return roll + "-" + milestone + "-v" + version.getVersionNo() + extension;
    }

    /** Exposed for the allocation-aware dashboards. */
    @Transactional(readOnly = true)
    public boolean hasLiveAllocation(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .map(a -> AllocationStatus.OCCUPIES_A_SEAT.contains(a.getStatus()))
                .orElse(false);
    }
}
