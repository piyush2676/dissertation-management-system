package com.dms.viva;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.user.Programme;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VivaService {

    private final VivaScheduleRepository vivaRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final UserRepository userRepository;

    /**
     * Books, or moves, a defence. Scheduling the same student twice updates the
     * existing booking rather than creating a second one -- the table is unique on
     * allocation, so a second row could not be written anyway.
     */
    public VivaSchedule schedule(String coordinatorEmail, Long allocationId,
                                 Instant scheduledAt, String venue, String panel) {

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
        viva.setPanel(panel == null || panel.isBlank() ? null : panel.strip());
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

    @Transactional(readOnly = true)
    public Optional<VivaSchedule> forStudent(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .flatMap(vivaRepository::findByAllocation);
    }
}
