package com.dms.evaluation;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.common.NotFoundException;
import com.dms.session.AcademicSession;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import com.dms.user.UserRepository;
import com.dms.viva.VivaScheduleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_EMAIL = "other@college.edu";

    @Mock private RubricCriterionRepository rubricRepository;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private AllocationService allocationService;
    @Mock private VivaScheduleRepository vivaRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private EvaluationService service;

    @Test
    void fullMarksOnEveryCriterionGivesTheSumOfTheWeights() {
        stubScoring();

        Evaluation evaluation = service.score(GUIDE_EMAIL, 1L, Map.of(1L, 10, 2L, 10), "excellent");

        assertEquals(new BigDecimal("100.00"), evaluation.getTotal(),
                "weights are 60 and 40, so full marks must total 100");
    }

    @Test
    void marksAreWeightedByEachCriterionShare() {
        stubScoring();

        // 5/10 on the 60-weight criterion, 10/10 on the 40-weight one = 30 + 40.
        Evaluation evaluation = service.score(GUIDE_EMAIL, 1L, Map.of(1L, 5, 2L, 10), null);

        assertEquals(new BigDecimal("70.00"), evaluation.getTotal());
    }

    @Test
    void scoresAreStoredKeyedByCriterionId() {
        stubScoring();

        Evaluation evaluation = service.score(GUIDE_EMAIL, 1L, Map.of(1L, 7, 2L, 9), null);

        assertEquals(Map.of("1", 7, "2", 9), evaluation.getScores());
    }

    @Test
    void aScoreAboveTheCriterionMaximumIsRefused() {
        stubScoring();

        assertThrows(IllegalArgumentException.class,
                () -> service.score(GUIDE_EMAIL, 1L, Map.of(1L, 11, 2L, 5), null));
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void aNegativeScoreIsRefused() {
        stubScoring();

        assertThrows(IllegalArgumentException.class,
                () -> service.score(GUIDE_EMAIL, 1L, Map.of(1L, -1, 2L, 5), null));
    }

    @Test
    void everyCriterionMustBeScored() {
        stubScoring();

        assertThrows(IllegalArgumentException.class,
                () -> service.score(GUIDE_EMAIL, 1L, Map.of(1L, 8), null));
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void anotherGuideCannotScoreSomeoneElsesStudent() {
        when(allocationRepository.findWithGraphById(1L)).thenReturn(Optional.of(allocation()));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(1L, OTHER_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> service.score(OTHER_EMAIL, 1L, Map.of(1L, 8, 2L, 8), null));
    }

    @Test
    void scoringWithNoRubricInPlaceIsRefused() {
        when(allocationRepository.findWithGraphById(1L)).thenReturn(Optional.of(allocation()));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(1L, GUIDE_EMAIL)).thenReturn(true);
        when(rubricRepository.findBySessionOrderBySequenceNoAsc(any())).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> service.score(GUIDE_EMAIL, 1L, Map.of(1L, 8), null));
    }

    @Test
    void rescoringReplacesTheEarlierMarksRatherThanAddingASecondSet() {
        stubScoring();
        Evaluation existing = new Evaluation();
        existing.setId(99L);
        when(evaluationRepository.findByAllocationAndExaminer(any(), any()))
                .thenReturn(Optional.of(existing));

        Evaluation evaluation = service.score(GUIDE_EMAIL, 1L, Map.of(1L, 6, 2L, 6), null);

        assertEquals(99L, evaluation.getId(), "the existing row must be updated in place");
    }

    // ---- fixtures -----------------------------------------------------------

    private void stubScoring() {
        Allocation allocation = allocation();
        when(allocationRepository.findWithGraphById(1L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(1L, GUIDE_EMAIL)).thenReturn(true);
        when(rubricRepository.findBySessionOrderBySequenceNoAsc(any()))
                .thenReturn(List.of(criterion(1L, "Methodology", 10, 60), criterion(2L, "Viva", 10, 40)));
        // Lenient: the validation tests throw before reaching either of these.
        lenient().when(userRepository.findByEmail(GUIDE_EMAIL)).thenReturn(Optional.of(user("Dr Test")));
        lenient().when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(String name) {
        User user = new User();
        user.setId(7L);
        user.setEmail(GUIDE_EMAIL);
        user.setFullName(name);
        return user;
    }

    private AcademicSession session() {
        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2026-27");
        session.setProgramme(Programme.MTECH);
        return session;
    }

    private Allocation allocation() {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setUser(user("Test Student"));

        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(user("Dr Test"));

        Allocation allocation = new Allocation();
        allocation.setId(1L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session());
        allocation.setStatus(AllocationStatus.ACCEPTED);
        return allocation;
    }

    private RubricCriterion criterion(Long id, String name, int maxMarks, int weightage) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setId(id);
        criterion.setName(name);
        criterion.setMaxMarks(maxMarks);
        criterion.setWeightage(weightage);
        criterion.setSession(session());
        return criterion;
    }
}
