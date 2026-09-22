package com.dms.recommendation;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.session.AcademicSession;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_GUIDE_EMAIL = "other@college.edu";

    @Mock private RecommendationRepository recommendationRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private RecommendationService service;

    @Test
    void theSupervisorFilesTheSheetAndItIsStamped() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        givenSupervisedBy(allocation, GUIDE_EMAIL);
        when(recommendationRepository.findByAllocation(allocation)).thenReturn(Optional.empty());
        givenSaveEchoesItsArgument();

        Recommendation filed = service.file(GUIDE_EMAIL, 11L, form(Verdict.ACCEPTABLE, null));

        assertEquals(Verdict.ACCEPTABLE, filed.getVerdict());
        assertEquals(GUIDE_EMAIL, filed.getSubmittedBy().getEmail());
        ArgumentCaptor<DomainEvents.RecommendationFiled> published =
                ArgumentCaptor.forClass(DomainEvents.RecommendationFiled.class);
        verify(events).publishEvent(published.capture());
        assertEquals("RECOMMENDATION_FILED", published.getValue().action());
    }

    @Test
    void filingAgainRevisesTheSheetAndKeepsWhoFiledItFirst() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        givenSupervisedBy(allocation, GUIDE_EMAIL);
        Recommendation existing = new Recommendation();
        existing.setId(3L);
        existing.setAllocation(allocation);
        existing.setVerdict(Verdict.MAJOR_REVISIONS);
        User first = user(7L, GUIDE_EMAIL, "Dr Test");
        existing.setSubmittedBy(first);
        Instant originally = Instant.parse("2026-09-01T10:00:00Z");
        existing.setSubmittedAt(originally);
        when(recommendationRepository.findByAllocation(allocation)).thenReturn(Optional.of(existing));
        givenSaveEchoesItsArgument();

        Recommendation revised = service.file(GUIDE_EMAIL, 11L, form(Verdict.ACCEPTABLE, null));

        assertEquals(3L, revised.getId(), "the existing sheet is updated in place");
        assertEquals(originally, revised.getSubmittedAt(), "when it was first filed does not move");
        ArgumentCaptor<DomainEvents.RecommendationFiled> published =
                ArgumentCaptor.forClass(DomainEvents.RecommendationFiled.class);
        verify(events).publishEvent(published.capture());
        assertEquals("RECOMMENDATION_REVISED", published.getValue().action());
    }

    @Test
    void anotherGuideCannotFileOnSomeoneElsesStudent() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(11L, OTHER_GUIDE_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> service.file(OTHER_GUIDE_EMAIL, 11L, form(Verdict.ACCEPTABLE, null)));
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void aStudentWhoIsNoLongerAllocatedCannotBeSummarised() {
        Allocation allocation = allocation(AllocationStatus.WITHDRAWN);
        givenSupervisedBy(allocation, GUIDE_EMAIL);

        assertThrows(IllegalStateException.class,
                () -> service.file(GUIDE_EMAIL, 11L, form(Verdict.ACCEPTABLE, null)));
    }

    @Test
    void aMissingSheetIsNotFoundForTheCoordinatorRatherThanEmpty() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(recommendationRepository.findByAllocation(allocation)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.forCoordinator(11L));
    }

    @Test
    void theFormDemandsAReasonWhenTheThesisIsNotAcceptableAsItIs() {
        assertTrue(form(Verdict.ACCEPTABLE, null).isQueriesPresentWhenSendingBack());
        assertFalse(form(Verdict.MINOR_REVISIONS, null).isQueriesPresentWhenSendingBack());
        assertFalse(form(Verdict.MAJOR_REVISIONS, "   ").isQueriesPresentWhenSendingBack());
        assertTrue(form(Verdict.REJECTED, "No original contribution.").isQueriesPresentWhenSendingBack());
    }

    @Test
    void onlyAAndBClearTheThesisForTheDefence() {
        assertTrue(Verdict.ACCEPTABLE.clearsForDefence());
        assertTrue(Verdict.MINOR_REVISIONS.clearsForDefence());
        assertFalse(Verdict.MAJOR_REVISIONS.clearsForDefence());
        assertFalse(Verdict.REJECTED.clearsForDefence());
    }

    // ---- fixtures -----------------------------------------------------------

    private void givenSupervisedBy(Allocation allocation, String email) {
        when(allocationRepository.findWithGraphById(11L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(11L, email)).thenReturn(true);
    }

    private void givenSaveEchoesItsArgument() {
        when(userRepository.findByEmail(GUIDE_EMAIL)).thenReturn(Optional.of(user(7L, GUIDE_EMAIL, "Dr Test")));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RecommendationForm form(Verdict verdict, String queries) {
        RecommendationForm form = new RecommendationForm();
        form.setVerdict(verdict);
        form.setQueries(queries);
        form.setTechnicalContent("Sound method, results validated against two baselines.");
        return form;
    }

    private static User user(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private static Allocation allocation(AllocationStatus status) {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setProgramme(Programme.MTECH);
        student.setSemester(4);
        student.setUser(user(1L, "student@college.edu", "Test Student"));
        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(user(7L, GUIDE_EMAIL, "Dr Test"));
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
