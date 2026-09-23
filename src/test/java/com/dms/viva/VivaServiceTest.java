package com.dms.viva;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.panel.PanelBoard;
import com.dms.panel.PanelService;
import com.dms.readiness.ReadinessService;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Mock private PanelService panelService;

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

    @Test
    void onlyExternalExaminersAreStoredAsText() {
        Allocation allocation = allocation();
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(readinessService.internalMarksMet(allocation)).thenReturn(true);
        when(userRepository.findByEmail(COORDINATOR_EMAIL)).thenReturn(Optional.of(new User()));
        when(vivaRepository.findByAllocation(allocation)).thenReturn(Optional.empty());
        when(vivaRepository.save(any(VivaSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        VivaSchedule named = service.schedule(COORDINATOR_EMAIL, 11L, Instant.now().plusSeconds(86400),
                "Seminar Hall", "  Dr C Rao, IIT Delhi  ");
        assertEquals("Dr C Rao, IIT Delhi", named.getExternalExaminers());

        VivaSchedule blank = service.schedule(COORDINATOR_EMAIL, 11L, Instant.now().plusSeconds(86400),
                "Seminar Hall", "   ");
        assertNull(blank.getExternalExaminers());
    }

    @Test
    void theInternalPanelIsReadFromPanelMembersNotTheBooking() {
        Allocation allocation = allocation();
        VivaSchedule viva = new VivaSchedule();
        viva.setAllocation(allocation);
        viva.setExternalExaminers("Dr C Rao");
        List<PanelBoard.MemberRow> appointed = List.of(
                new PanelBoard.MemberRow(5L, "Dr A Sharma", "guide2@college.edu", Instant.now()));
        when(panelService.membersOf(allocation)).thenReturn(appointed);
        when(panelService.membersByAllocation(List.of(allocation))).thenReturn(Map.of(11L, appointed));

        assertEquals(appointed, service.panelFor(viva));
        assertEquals(Map.of(11L, appointed), service.panelsFor(List.of(viva)));
    }

    private static Allocation allocation() {
        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStatus(AllocationStatus.ACCEPTED);
        return allocation;
    }
}
