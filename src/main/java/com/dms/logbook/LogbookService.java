package com.dms.logbook;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.Digests;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dissertation logbook: the student records each meeting with their guide,
 * the guide countersigns. Authorisation is by ownership, checked here, the same
 * way submissions and comments do it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LogbookService {

    private final LogbookEntryRepository entryRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    // ---- student ------------------------------------------------------------

    @Transactional(readOnly = true)
    public LogbookBoard boardFor(String studentEmail) {
        return liveAllocationFor(studentEmail).map(this::board).orElse(LogbookBoard.none());
    }

    /** A new row after a meeting. Numbered after the last one; the student never picks the number. */
    public LogbookEntry record(String studentEmail, LogbookEntryForm form) {
        Allocation allocation = liveAllocationFor(studentEmail)
                .orElseThrow(() -> new IllegalStateException("You need a guide before you can keep a logbook."));

        int next = entryRepository.findFirstByAllocationOrderByMeetingNoDesc(allocation)
                .map(e -> e.getMeetingNo() + 1)
                .orElse(1);

        LogbookEntry entry = new LogbookEntry();
        entry.setAllocation(allocation);
        entry.setMeetingNo(next);
        entry.setStatus(LogbookEntryStatus.PENDING);
        apply(entry, form);
        LogbookEntry saved = entryRepository.save(entry);
        events.publishEvent(new DomainEvents.LogbookEntryRecorded(studentEmail, saved.getId(), saved.getMeetingNo()));
        return saved;
    }

    /**
     * The student corrects an unsigned row. A returned row goes back to PENDING so it
     * re-enters the guide's queue; a signed row cannot be touched -- that is the seal.
     */
    public LogbookEntry revise(String studentEmail, Long entryId, LogbookEntryForm form) {
        LogbookEntry entry = loadOwnedByStudent(entryId, studentEmail);
        if (!entry.getStatus().editableByStudent()) {
            throw new InvalidStateTransitionException(entry.getStatus(), LogbookEntryStatus.PENDING);
        }
        boolean resubmission = entry.getStatus() == LogbookEntryStatus.RETURNED;
        apply(entry, form);
        if (resubmission) {
            entry.setStatus(LogbookEntryStatus.PENDING);
            entry.setSupervisorRemarks(null);
        }
        LogbookEntry saved = entryRepository.save(entry);
        if (resubmission) {
            events.publishEvent(new DomainEvents.LogbookEntryRecorded(studentEmail, saved.getId(), saved.getMeetingNo()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public LogbookEntry loadForEdit(String studentEmail, Long entryId) {
        return loadOwnedByStudent(entryId, studentEmail);
    }

    // ---- supervisor ---------------------------------------------------------

    /** Unsigned rows across every student the guide currently supervises, oldest meeting first. */
    @Transactional(readOnly = true)
    public List<QueueItem> queueFor(String supervisorEmail) {
        List<QueueItem> items = new ArrayList<>();
        for (LogbookEntry entry : entryRepository.pendingFor(supervisorEmail, AllocationStatus.OCCUPIES_A_SEAT)) {
            items.add(new QueueItem(
                    entry.getAllocation().getId(),
                    entry.getAllocation().getStudent().getRollNo(),
                    entry.getAllocation().getStudent().getUser().getFullName(),
                    row(entry)));
        }
        return items;
    }

    public record QueueItem(Long allocationId, String rollNo, String studentName, LogbookBoard.Row row) {
    }

    @Transactional(readOnly = true)
    public long pendingCountFor(String supervisorEmail) {
        return entryRepository.countPendingFor(supervisorEmail, AllocationStatus.OCCUPIES_A_SEAT);
    }

    /** One supervised student's whole logbook. 404 rather than 403 for a student who is not theirs. */
    @Transactional(readOnly = true)
    public LogbookBoard boardForSupervisor(String supervisorEmail, Long allocationId) {
        if (!allocationRepository.existsByIdAndSupervisorUserEmail(allocationId, supervisorEmail)) {
            throw new NotFoundException("Allocation", allocationId);
        }
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        return board(allocation);
    }

    /**
     * Countersign or return. Signing freezes the row and stores its digest; the
     * certificate lists that digest, so the seal is verifiable, not just a flag.
     */
    public LogbookEntry decide(String supervisorEmail, Long entryId, LogbookSignForm form) {
        LogbookEntry entry = entryRepository.findWithGraphById(entryId)
                .orElseThrow(() -> new NotFoundException("Logbook entry", entryId));
        if (!entryRepository.existsByIdAndAllocationSupervisorUserEmail(entryId, supervisorEmail)) {
            throw new NotFoundException("Logbook entry", entryId);
        }
        LogbookEntryStatus target = form.getDecision();
        if (target == null || !entry.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(entry.getStatus(), target);
        }
        User guide = userRepository.findByEmail(supervisorEmail)
                .orElseThrow(() -> new NotFoundException("User " + supervisorEmail + " not found"));

        String remarks = form.getRemarks() == null || form.getRemarks().isBlank() ? null : form.getRemarks().strip();
        entry.setSupervisorRemarks(remarks);
        entry.setStatus(target);

        String digest = null;
        if (target == LogbookEntryStatus.SIGNED) {
            entry.setSignedBy(guide);
            entry.setSignedAt(Instant.now());
            digest = digestOf(entry, guide.getEmail());
            entry.setEntryDigest(digest);
        }
        LogbookEntry saved = entryRepository.save(entry);
        events.publishEvent(new DomainEvents.LogbookEntryDecided(
                supervisorEmail, saved.getId(), saved.getMeetingNo(), target.name(), digest));
        return saved;
    }

    // ---- provenance ---------------------------------------------------------

    /** "meetingNo:digest" for every signed row, in meeting order. Empty when none is signed. */
    @Transactional(readOnly = true)
    public List<String> sealedFactsFor(Allocation allocation) {
        return entryRepository.findByAllocationAndStatusOrderByMeetingNoAsc(allocation, LogbookEntryStatus.SIGNED)
                .stream()
                .map(e -> e.getMeetingNo() + ":" + e.getEntryDigest())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LogbookBoard.Row> signedRowsFor(Allocation allocation) {
        return entryRepository.findByAllocationAndStatusOrderByMeetingNoAsc(allocation, LogbookEntryStatus.SIGNED)
                .stream()
                .map(this::row)
                .toList();
    }

    /**
     * The canonical form of a signed row. Instants are written as ISO-8601 so the
     * digest does not depend on a zone or a display format, and the signer is the
     * email, not the display name, for the same reason.
     */
    static String digestOf(LogbookEntry entry, String signerEmail) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("allocation", String.valueOf(entry.getAllocation().getId()));
        facts.put("meetingNo", String.valueOf(entry.getMeetingNo()));
        facts.put("meetingAt", entry.getMeetingAt().toString());
        facts.put("workAssigned", entry.getWorkAssigned());
        facts.put("workCompleted", entry.getWorkCompleted());
        facts.put("challenges", entry.getChallenges());
        facts.put("supervisorRemarks", entry.getSupervisorRemarks());
        facts.put("signedBy", signerEmail);
        facts.put("signedAt", entry.getSignedAt().toString());
        return Digests.sha256Of(facts);
    }

    // ---- helpers ------------------------------------------------------------

    private Optional<Allocation> liveAllocationFor(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .filter(a -> a.getStatus().occupiesASeat());
    }

    private LogbookBoard board(Allocation allocation) {
        List<LogbookBoard.Row> rows = entryRepository.findByAllocationOrderByMeetingNoAsc(allocation).stream()
                .map(this::row)
                .toList();
        int next = rows.isEmpty() ? 1 : rows.get(rows.size() - 1).meetingNo() + 1;
        return new LogbookBoard(
                true,
                allocation.getId(),
                allocation.getSession().getLabel(),
                allocation.getStudent().getRollNo(),
                allocation.getStudent().getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getThesisCode(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                allocation.getSupervisor().getUser().getFullName(),
                allocation.getCoSupervisor() == null ? null : allocation.getCoSupervisor().getUser().getFullName(),
                rows,
                next);
    }

    private LogbookBoard.Row row(LogbookEntry e) {
        return new LogbookBoard.Row(
                e.getId(),
                e.getMeetingNo(),
                e.getMeetingAt(),
                e.getWorkAssigned(),
                e.getWorkCompleted(),
                e.getChallenges(),
                e.getStatus(),
                e.getSupervisorRemarks(),
                e.getSignedBy() == null ? null : e.getSignedBy().getFullName(),
                e.getSignedAt(),
                e.getEntryDigest());
    }

    private static void apply(LogbookEntry entry, LogbookEntryForm form) {
        entry.setMeetingAt(form.getMeetingAt().atZone(ZoneId.systemDefault()).toInstant());
        entry.setWorkAssigned(form.getWorkAssigned().strip());
        entry.setWorkCompleted(form.getWorkCompleted().strip());
        entry.setChallenges(form.getChallenges() == null || form.getChallenges().isBlank()
                ? null : form.getChallenges().strip());
    }

    private LogbookEntry loadOwnedByStudent(Long entryId, String studentEmail) {
        LogbookEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Logbook entry", entryId));
        if (!entryRepository.existsByIdAndAllocationStudentUserEmail(entryId, studentEmail)) {
            throw new NotFoundException("Logbook entry", entryId);
        }
        return entry;
    }
}
