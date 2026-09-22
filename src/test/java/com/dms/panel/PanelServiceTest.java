package com.dms.panel;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.session.AcademicSession;
import com.dms.user.Programme;
import com.dms.user.Role;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PanelServiceTest {

    private static final String COORDINATOR_EMAIL = "coordinator@college.edu";

    @Mock private PanelMemberRepository panelRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private AllocationService allocationService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private PanelService service;

    @Test
    void aGuideCannotSitOnTheirOwnStudentsPanel() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(userRepository.findById(7L)).thenReturn(Optional.of(faculty(7L, "guide@college.edu", "Dr Test")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.add(COORDINATOR_EMAIL, 11L, 7L));

        assertTrue(ex.getMessage().contains("count one opinion twice"),
                "the rule is about the average, and the message should say so");
        verify(panelRepository, never()).save(any());
    }

    @Test
    void neitherCanTheCoSupervisor() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        SupervisorProfile co = new SupervisorProfile();
        co.setId(8L);
        co.setUser(faculty(8L, "guide2@college.edu", "Dr Other"));
        allocation.setCoSupervisor(co);
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(userRepository.findById(8L)).thenReturn(Optional.of(co.getUser()));

        assertThrows(IllegalArgumentException.class, () -> service.add(COORDINATOR_EMAIL, 11L, 8L));
        verify(panelRepository, never()).save(any());
    }

    @Test
    void anotherFacultyMemberIsAppointedAndRecorded() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        User other = faculty(9L, "reviewer@college.edu", "Dr Reviewer");
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(userRepository.findById(9L)).thenReturn(Optional.of(other));
        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(faculty(2L, COORDINATOR_EMAIL, "PG Coordinator")));
        when(panelRepository.save(any(PanelMember.class))).thenAnswer(inv -> inv.getArgument(0));

        PanelMember member = service.add(COORDINATOR_EMAIL, 11L, 9L);

        assertSame(other, member.getMember());
        assertEquals(COORDINATOR_EMAIL, member.getAddedBy().getEmail());
        verify(events).publishEvent(any(DomainEvents.PanelMemberAdded.class));
    }

    @Test
    void aStudentCannotBeAppointed() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        User student = new User();
        student.setId(20L);
        student.setEmail("student2@college.edu");
        student.setFullName("Another Student");
        student.setRoles(Set.of(Role.STUDENT));
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(userRepository.findById(20L)).thenReturn(Optional.of(student));

        assertThrows(IllegalArgumentException.class, () -> service.add(COORDINATOR_EMAIL, 11L, 20L));
    }

    @Test
    void appointingTheSamePersonTwiceIsRefusedByTheDatabase() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(userRepository.findById(9L)).thenReturn(Optional.of(faculty(9L, "reviewer@college.edu", "Dr Reviewer")));
        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(faculty(2L, COORDINATOR_EMAIL, "PG Coordinator")));
        when(panelRepository.save(any(PanelMember.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(IllegalStateException.class, () -> service.add(COORDINATOR_EMAIL, 11L, 9L));
    }

    @Test
    void aStudentWithoutALiveAllocationHasNoPanel() {
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation(AllocationStatus.REQUESTED)));

        assertThrows(IllegalStateException.class, () -> service.add(COORDINATOR_EMAIL, 11L, 9L));
    }

    @Test
    void removingAMemberLeavesTheirMarksAlone() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        PanelMember member = new PanelMember();
        member.setId(3L);
        member.setAllocation(allocation);
        member.setMember(faculty(9L, "reviewer@college.edu", "Dr Reviewer"));
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(panelRepository.findByAllocationAndMemberId(allocation, 9L)).thenReturn(Optional.of(member));

        service.remove(COORDINATOR_EMAIL, 11L, 9L);

        verify(panelRepository).delete(member);
        verify(events).publishEvent(any(DomainEvents.PanelMemberRemoved.class));
    }

    // ---- fixtures -----------------------------------------------------------

    private static User faculty(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        user.setRoles(Set.of(Role.SUPERVISOR, Role.REVIEWER));
        return user;
    }

    private static Allocation allocation(AllocationStatus status) {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setProgramme(Programme.MTECH);
        student.setSemester(4);
        User studentUser = new User();
        studentUser.setId(1L);
        studentUser.setEmail("student@college.edu");
        studentUser.setFullName("Test Student");
        student.setUser(studentUser);

        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(faculty(7L, "guide@college.edu", "Dr Test"));

        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2026-27");

        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session);
        allocation.setStatus(status);
        return allocation;
    }
}
