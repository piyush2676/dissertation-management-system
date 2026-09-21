package com.dms.viva;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.readiness.ReadinessService;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VivaServiceTest {

    private static final String COORDINATOR_EMAIL = "coordinator@college.edu";

    @Mock private VivaScheduleRepository vivaRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private AllocationService allocationService;
    @Mock private UserRepository userRepository;
    @Mock private ReadinessService readinessService;

    @InjectMocks private VivaService service;

    @Test
    void theVivaCannotBeBookedBelowHalfTheInternalMarks() {
        Allocation allocation = allocation();
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(readinessService.internalMarksMet(allocation)).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.schedule(COORDINATOR_EMAIL, 11L, Instant.now().plusSeconds(86400), "Seminar Hall", null));

        assertEquals("That student has not secured 50% of the internal marks, so the external viva cannot be booked yet.",
                ex.getMessage());
        verify(vivaRepository, never()).save(any());
    }

    @Test
    void atHalfTheInternalMarksTheVivaIsBooked() {
        Allocation allocation = allocation();
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(readinessService.internalMarksMet(allocation)).thenReturn(true);
        User coordinator = new User();
        coordinator.setEmail(COORDINATOR_EMAIL);
        when(userRepository.findByEmail(COORDINATOR_EMAIL)).thenReturn(Optional.of(coordinator));
        when(vivaRepository.findByAllocation(allocation)).thenReturn(Optional.empty());
        when(vivaRepository.save(any(VivaSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        VivaSchedule viva = service.schedule(COORDINATOR_EMAIL, 11L, Instant.now().plusSeconds(86400), "Seminar Hall", null);

        assertEquals(VivaStatus.SCHEDULED, viva.getStatus());
    }

    private static Allocation allocation() {
        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStatus(AllocationStatus.ACCEPTED);
        return allocation;
    }
}
