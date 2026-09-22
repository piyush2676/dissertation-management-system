package com.dms.change;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Section 4.11: changing a supervisor or a thesis title.
 *
 * <p>This service is the only door to two transitions the rest of the system
 * refuses. Approving a supervisor change withdraws a live allocation, which frees
 * the partial unique index so the coordinator can place the scholar again;
 * approving a title change sends an approved topic back for revision, so the
 * normal proposal and approval runs once more. Both moves are legal in the state
 * machines and unreachable from anywhere else: {@code AllocationService.withdraw}
 * still refuses anything but a pending request, and {@code TopicService.decide}
 * starts from PROPOSED.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChangeRequestService {

    private final ChangeRequestRepository requestRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final TopicRepository topicRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    // ---- student ------------------------------------------------------------

    @Transactional(readOnly = true)
    public ChangeRequestBoard boardFor(String studentEmail) {
        return liveAllocationFor(studentEmail).map(this::board).orElse(ChangeRequestBoard.none());
    }

    public ChangeRequest raise(String studentEmail, ChangeRequestForm form) {
        Allocation allocation = liveAllocationFor(studentEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "You need a live allocation before you can ask to change it."));

        if (requestRepository.existsByAllocationAndStatus(allocation, ChangeRequestStatus.PENDING)) {
            throw new IllegalStateException("You already have a request with the committee.");
        }

        SupervisorProfile preferred = null;
        if (form.getKind() == ChangeKind.SUPERVISOR && form.getPreferredSupervisorId() != null) {
            preferred = supervisorProfileRepository.findById(form.getPreferredSupervisorId())
                    .orElseThrow(() -> new NotFoundException("Supervisor", form.getPreferredSupervisorId()));
            if (preferred.getId().equals(allocation.getSupervisor().getId())) {
                throw new IllegalArgumentException("That is the guide you already have.");
            }
        }

        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new NotFoundException("User " + studentEmail + " not found"));

        ChangeRequest request = new ChangeRequest();
        request.setAllocation(allocation);
        request.setKind(form.getKind());
        request.setReason(form.getReason().strip());
        request.setPreferredSupervisor(preferred);
        request.setProposedTitle(form.getKind() == ChangeKind.TITLE ? form.getProposedTitle().strip() : null);
        request.setStatus(ChangeRequestStatus.PENDING);
        request.setRequestedBy(student);
        request.setRequestedAt(Instant.now());

        try {
            ChangeRequest saved = requestRepository.save(request);
            events.publishEvent(new DomainEvents.ChangeRequested(
                    studentEmail, saved.getId(), saved.getKind().name()));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("You already have a request with the committee.");
        }
    }

    // ---- coordinator --------------------------------------------------------

    public record QueueItem(Long id, String rollNo, String studentName, Long allocationId,
                            String currentSupervisor, String topicTitle, ChangeRequestBoard.Row row) {
    }

    @Transactional(readOnly = true)
    public List<QueueItem> queue() {
        List<QueueItem> items = new ArrayList<>();
        for (ChangeRequest request : requestRepository.findByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)) {
            Allocation allocation = request.getAllocation();
            items.add(new QueueItem(
                    request.getId(),
                    allocation.getStudent().getRollNo(),
                    allocation.getStudent().getUser().getFullName(),
                    allocation.getId(),
                    allocation.getSupervisor().getUser().getFullName(),
                    allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                    row(request)));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requestRepository.countByStatus(ChangeRequestStatus.PENDING);
    }

    /**
     * The committee's answer. Approving is what performs the change: a supervisor
     * change withdraws the placement and leaves the scholar for the coordinator to
     * place again, and a title change returns the topic to the student to
     * re-propose. Neither touches anything until this point.
     */
    public ChangeRequest decide(String coordinatorEmail, Long requestId, ChangeRequestDecisionForm form) {
        ChangeRequest request = requestRepository.findWithGraphById(requestId)
                .orElseThrow(() -> new NotFoundException("Change request", requestId));

        ChangeRequestStatus target = form.getDecision();
        if (target == null || !request.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(request.getStatus(), target);
        }

        User coordinator = userRepository.findByEmail(coordinatorEmail)
                .orElseThrow(() -> new NotFoundException("User " + coordinatorEmail + " not found"));

        if (target == ChangeRequestStatus.APPROVED) {
            if (request.getKind() == ChangeKind.SUPERVISOR) {
                withdrawPlacement(request.getAllocation(), coordinator);
            } else {
                returnTitleForRevision(request.getAllocation(), request);
            }
        }

        request.setStatus(target);
        request.setDecisionNote(form.getNote() == null || form.getNote().isBlank() ? null : form.getNote().strip());
        request.setDecidedBy(coordinator);
        request.setDecidedAt(Instant.now());

        ChangeRequest decided = requestRepository.save(request);
        events.publishEvent(new DomainEvents.ChangeRequestDecided(
                coordinatorEmail, decided.getId(), decided.getKind().name(), target.name()));
        return decided;
    }

    /**
     * The transition the rest of the system refuses. Capacity frees up with it, and
     * the partial unique index stops blocking a second placement, so the coordinator
     * can allocate on the usual page.
     */
    private void withdrawPlacement(Allocation allocation, User coordinator) {
        if (!allocation.getStatus().canTransitionTo(AllocationStatus.WITHDRAWN)) {
            throw new InvalidStateTransitionException(allocation.getStatus(), AllocationStatus.WITHDRAWN);
        }
        AllocationStatus from = allocation.getStatus();
        allocation.setStatus(AllocationStatus.WITHDRAWN);
        allocation.setDecidedAt(Instant.now());
        allocation.setAllocatedBy(coordinator);
        allocationRepository.save(allocation);
        events.publishEvent(new DomainEvents.GuideDecided(coordinator.getEmail(), allocation.getId(),
                from.name(), AllocationStatus.WITHDRAWN.name()));
    }

    /**
     * The other refused transition. The thesis code survives -- Annexures 3 to 5
     * print it and the scholar is the same scholar -- and the round number goes up
     * when they propose again, so the history reads as one dissertation.
     */
    private void returnTitleForRevision(Allocation allocation, ChangeRequest request) {
        Topic topic = allocation.getTopic();
        if (topic == null) {
            topic = topicRepository.findFirstByStudentOrderByCreatedAtDesc(allocation.getStudent())
                    .orElseThrow(() -> new IllegalStateException("There is no topic on record to change."));
        }
        if (!topic.getStatus().canTransitionTo(TopicStatus.CHANGES_REQUESTED)) {
            throw new InvalidStateTransitionException(topic.getStatus(), TopicStatus.CHANGES_REQUESTED);
        }
        topic.setStatus(TopicStatus.CHANGES_REQUESTED);
        topic.setDecisionReason("Title change approved by the committee: " + request.getReason());
        topic.setDecidedAt(Instant.now());
        topicRepository.save(topic);
        events.publishEvent(new DomainEvents.TopicDecided(request.getRequestedBy().getEmail(), topic.getId(),
                TopicStatus.APPROVED.name(), TopicStatus.CHANGES_REQUESTED.name()));
    }

    // ---- helpers ------------------------------------------------------------

    private Optional<Allocation> liveAllocationFor(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .filter(a -> a.getStatus().occupiesASeat());
    }

    private ChangeRequestBoard board(Allocation allocation) {
        List<ChangeRequestBoard.Row> rows = requestRepository
                .findByAllocationOrderByRequestedAtDesc(allocation).stream()
                .map(this::row)
                .toList();
        return new ChangeRequestBoard(
                true,
                allocation.getId(),
                allocation.getSupervisor().getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                allocation.getTopic() == null ? null : allocation.getTopic().getThesisCode(),
                rows.stream().anyMatch(ChangeRequestBoard.Row::open),
                rows);
    }

    private ChangeRequestBoard.Row row(ChangeRequest r) {
        return new ChangeRequestBoard.Row(
                r.getId(),
                r.getKind(),
                r.getReason(),
                r.getPreferredSupervisor() == null ? null : r.getPreferredSupervisor().getUser().getFullName(),
                r.getProposedTitle(),
                r.getStatus(),
                r.getDecisionNote(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getFullName(),
                r.getRequestedAt(),
                r.getDecidedAt());
    }
}
