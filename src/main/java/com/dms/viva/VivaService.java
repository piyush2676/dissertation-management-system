package com.dms.viva;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.panel.PanelBoard;
import com.dms.panel.PanelService;
import com.dms.readiness.ReadinessService;
import com.dms.user.Programme;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VivaService {

    private final VivaScheduleRepository vivaRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final UserRepository userRepository;
    private final ReadinessService readinessService;
    private final PanelService panelService;

    /**
     * Books, or moves, a defence. Scheduling the same student twice updates the
     * existing booking rather than creating a second one -- the table is unique on
     * allocation, so a second row could not be written anyway.
     */
    public VivaSchedule schedule(String coordinatorEmail, Long allocationId,
                                 Instant scheduledAt, String venue, String externalExaminers) {

        if (scheduledAt == null) {
            throw new IllegalArgumentException("Pick a date and time.");
        }
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("A venue is required.");
        }

        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));

        if (!allocation.getStatus().occupiesASeat()) {
            throw new IllegalStateException("That student does not have a live allocation.");
        }
        // Guidelines section 7.1: half the internal marks before the external viva. The one
        // readiness rule that is a gate rather than a line on the ledger.
        if (!readinessService.internalMarksMet(allocation)) {
            throw new IllegalStateException(
                    "That student has not secured 50% of the internal marks, so the external viva cannot be booked yet.");
        }

        User coordinator = userRepository.findByEmail(coordinatorEmail)
                .orElseThrow(() -> new NotFoundException("User " + coordinatorEmail + " not found"));

        Optional<VivaSchedule> existing = vivaRepository.findByAllocation(allocation);

        VivaSchedule viva;
        if (existing.isPresent()) {
            viva = existing.get();
            if (viva.getStatus().isTerminal()) {
                throw new InvalidStateTransitionException(viva.getStatus(), VivaStatus.RESCHEDULED);
            }
            viva.setStatus(VivaStatus.RESCHEDULED);
        } else {
            viva = new VivaSchedule();
            viva.setAllocation(allocation);
            viva.setStatus(VivaStatus.SCHEDULED);
        }

        viva.setScheduledAt(scheduledAt);
        viva.setVenue(venue.strip());
        viva.setExternalExaminers(externalExaminers == null || externalExaminers.isBlank()
                ? null : externalExaminers.strip());
        viva.setScheduledBy(coordinator);
        viva.setUpdatedAt(Instant.now());
        return vivaRepository.save(viva);
    }

    public VivaSchedule mark(String coordinatorEmail, Long vivaId, VivaStatus target) {
        VivaSchedule viva = vivaRepository.findById(vivaId)
                .orElseThrow(() -> new NotFoundException("Viva", vivaId));

        if (!viva.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(viva.getStatus(), target);
        }

        viva.setStatus(target);
        viva.setUpdatedAt(Instant.now());
        return vivaRepository.save(viva);
    }

    @Transactional(readOnly = true)
    public List<VivaSchedule> scheduleFor(Programme programme) {
        List<Allocation> cohort = allocationService.cohortFor(programme);
        if (cohort.isEmpty()) {
            return List.of();
        }
        return vivaRepository.findByAllocationSessionOrderByScheduledAtAsc(cohort.get(0).getSession());
    }

    /** The internal panel for each booking, keyed by allocation id. */
    @Transactional(readOnly = true)
    public Map<Long, List<PanelBoard.MemberRow>> panelsFor(List<VivaSchedule> schedules) {
        return panelService.membersByAllocation(
                schedules.stream().map(VivaSchedule::getAllocation).toList());
    }

    @Transactional(readOnly = true)
    public List<PanelBoard.MemberRow> panelFor(VivaSchedule viva) {
        return panelService.membersOf(viva.getAllocation());
    }

    @Transactional(readOnly = true)
    public Optional<VivaSchedule> forStudent(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .flatMap(vivaRepository::findByAllocation);
    }
}
