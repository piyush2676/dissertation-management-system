package com.dms.outcome;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Papers, patents and products: the student reports, the coordinator verifies.
 * The rules in {@code readiness} only ever count a row that is both achieved and
 * verified, so a student cannot satisfy the publication requirement by typing.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OutcomeService {

    private final OutcomeRepository outcomeRepository;
    private final AllocationService allocationService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    // ---- student ------------------------------------------------------------

    @Transactional(readOnly = true)
    public OutcomeBoard boardFor(String studentEmail) {
        return liveAllocationFor(studentEmail).map(this::board).orElse(OutcomeBoard.none());
    }

    public Outcome report(String studentEmail, OutcomeForm form) {
        Allocation allocation = liveAllocationFor(studentEmail)
                .orElseThrow(() -> new IllegalStateException("You need a guide before you can report an outcome."));
        Outcome outcome = new Outcome();
        outcome.setAllocation(allocation);
        apply(outcome, form);
        Outcome saved = outcomeRepository.save(outcome);
        events.publishEvent(new DomainEvents.OutcomeReported(studentEmail, saved.getId(), saved.getKind().name(), saved.getTitle()));
        return saved;
    }

    /** Editing clears any verification: the coordinator confirmed the old text, not the new one. */
    public Outcome revise(String studentEmail, Long outcomeId, OutcomeForm form) {
        Outcome outcome = loadOwnedByStudent(outcomeId, studentEmail);
        boolean wasVerified = outcome.isVerified();
        apply(outcome, form);
        outcome.setVerifiedBy(null);
        outcome.setVerifiedAt(null);
        outcome.setVerificationNote(null);
        Outcome saved = outcomeRepository.save(outcome);
        events.publishEvent(new DomainEvents.OutcomeReported(studentEmail, saved.getId(), saved.getKind().name(),
                (wasVerified ? "re-reported: " : "") + saved.getTitle()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Outcome loadForEdit(String studentEmail, Long outcomeId) {
        return loadOwnedByStudent(outcomeId, studentEmail);
    }

    // ---- coordinator --------------------------------------------------------

    public record QueueItem(Long allocationId, String rollNo, String studentName, OutcomeBoard.Row row) {
    }

    @Transactional(readOnly = true)
    public List<QueueItem> queue() {
        List<QueueItem> items = new ArrayList<>();
        for (Outcome outcome : outcomeRepository.unverified(AllocationStatus.OCCUPIES_A_SEAT)) {
            items.add(new QueueItem(
                    outcome.getAllocation().getId(),
                    outcome.getAllocation().getStudent().getRollNo(),
                    outcome.getAllocation().getStudent().getUser().getFullName(),
                    row(outcome)));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public long unverifiedCount() {
        return outcomeRepository.countUnverified(AllocationStatus.OCCUPIES_A_SEAT);
    }

    /**
     * Confirms the outcome against its evidence, or sends it back with what is
     * missing. Returning leaves the note on the row so the student sees it.
     */
    public Outcome verify(String coordinatorEmail, Long outcomeId, OutcomeVerifyForm form) {
        Outcome outcome = outcomeRepository.findWithGraphById(outcomeId)
                .orElseThrow(() -> new NotFoundException("Outcome", outcomeId));
        User coordinator = userRepository.findByEmail(coordinatorEmail)
                .orElseThrow(() -> new NotFoundException("User " + coordinatorEmail + " not found"));
        String note = form.getNote() == null || form.getNote().isBlank() ? null : form.getNote().strip();
        boolean verified = Boolean.TRUE.equals(form.getVerified());
        if (verified) {
            outcome.setVerifiedBy(coordinator);
            outcome.setVerifiedAt(Instant.now());
        } else {
            outcome.setVerifiedBy(null);
            outcome.setVerifiedAt(null);
        }
        outcome.setVerificationNote(note);
        Outcome saved = outcomeRepository.save(outcome);
        events.publishEvent(new DomainEvents.OutcomeVerified(coordinatorEmail, saved.getId(),
                verified, saved.getKind().name() + " " + saved.getTitle()));
        return saved;
    }

    // ---- rules and provenance -----------------------------------------------

    /** Rows the rules may count: achieved and verified. */
    @Transactional(readOnly = true)
    public List<OutcomeBoard.Row> countedRowsFor(Allocation allocation) {
        return outcomeRepository.findByAllocationAndVerifiedAtIsNotNullOrderByCreatedAtAsc(allocation).stream()
                .map(this::row)
                .filter(OutcomeBoard.Row::counts)
                .toList();
    }

    /** "kind/indexing/status/reference" for every verified row, for the sealed facts. */
    @Transactional(readOnly = true)
    public List<String> sealedFactsFor(Allocation allocation) {
        return outcomeRepository.findByAllocationAndVerifiedAtIsNotNullOrderByCreatedAtAsc(allocation).stream()
                .map(o -> o.getKind().name() + "/" + o.getIndexing().name() + "/" + o.getStatus().name()
                        + "/" + (o.getReference() == null ? "" : o.getReference())
                        + "/" + o.getVerifiedBy().getEmail() + "/" + o.getVerifiedAt())
                .toList();
    }

    // ---- helpers ------------------------------------------------------------

    private Optional<Allocation> liveAllocationFor(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .filter(a -> a.getStatus().occupiesASeat());
    }

    private OutcomeBoard board(Allocation allocation) {
        List<OutcomeBoard.Row> rows = outcomeRepository.findByAllocationOrderByCreatedAtAsc(allocation).stream()
                .map(this::row)
                .toList();
        return new OutcomeBoard(true, allocation.getId(),
                allocation.getStudent().getRollNo(),
                allocation.getStudent().getUser().getFullName(),
                rows);
    }

    public OutcomeBoard boardFor(Allocation allocation) {
        return board(allocation);
    }

    private OutcomeBoard.Row row(Outcome o) {
        return new OutcomeBoard.Row(
                o.getId(), o.getKind(), o.getTitle(), o.getVenue(), o.getIndexing(), o.getStatus(),
                o.getReference(), o.getOutcomeDate(), o.getNotes(),
                o.isVerified(),
                o.getVerifiedBy() == null ? null : o.getVerifiedBy().getFullName(),
                o.getVerifiedAt(),
                o.getVerificationNote());
    }

    private static void apply(Outcome outcome, OutcomeForm form) {
        outcome.setKind(form.getKind());
        outcome.setTitle(form.getTitle().strip());
        outcome.setVenue(blankToNull(form.getVenue()));
        outcome.setIndexing(form.getIndexing() == null ? OutcomeIndexing.NONE : form.getIndexing());
        outcome.setStatus(form.getStatus());
        outcome.setReference(blankToNull(form.getReference()));
        outcome.setOutcomeDate(form.getOutcomeDate());
        outcome.setNotes(blankToNull(form.getNotes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Outcome loadOwnedByStudent(Long outcomeId, String studentEmail) {
        Outcome outcome = outcomeRepository.findById(outcomeId)
                .orElseThrow(() -> new NotFoundException("Outcome", outcomeId));
        if (!outcomeRepository.existsByIdAndAllocationStudentUserEmail(outcomeId, studentEmail)) {
            throw new NotFoundException("Outcome", outcomeId);
        }
        return outcome;
    }
}
