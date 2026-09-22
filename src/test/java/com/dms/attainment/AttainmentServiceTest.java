package com.dms.attainment;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.evaluation.Evaluation;
import com.dms.evaluation.EvaluationRepository;
import com.dms.evaluation.RubricCriterion;
import com.dms.evaluation.RubricCriterionRepository;
import com.dms.session.AcademicSession;
import com.dms.session.DissertationPhase;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Attainment is a query over the rubric's CO codes and the examiners' marks, so
 * these pin the arithmetic the accreditation paperwork will be read against.
 */
@ExtendWith(MockitoExtension.class)
class AttainmentServiceTest {

    @Mock private AllocationService allocationService;
    @Mock private RubricCriterionRepository rubricRepository;
    @Mock private EvaluationRepository evaluationRepository;

    @InjectMocks private AttainmentService service;

    @Test
    void aCoIsAttainedByAStudentAtSixtyPercentOfItsMarks() {
        Allocation a = allocation(1L, 4);
        givenCohort(List.of(a));
        givenRubric(criterion(1L, "CO1", 10), criterion(2L, "CO1", 10));
        // 12 of 20 is 60%, exactly the threshold.
        when(evaluationRepository.findByAllocation(a)).thenReturn(List.of(evaluation(Map.of("1", 6, "2", 6))));

        AttainmentReport.Row row = service.reportFor(Programme.MTECH, DissertationPhase.FINAL).rows().get(0);

        assertEquals(1, row.studentsScored());
        assertEquals(1, row.studentsAttained());
        assertEquals(new BigDecimal("100.0"), row.attainmentPercent());
    }

    @Test
    void justUnderTheThresholdDoesNotAttain() {
        Allocation a = allocation(1L, 4);
        givenCohort(List.of(a));
        givenRubric(criterion(1L, "CO1", 10));
        when(evaluationRepository.findByAllocation(a)).thenReturn(List.of(evaluation(Map.of("1", 5))));

        AttainmentReport.Row row = service.reportFor(Programme.MTECH, DissertationPhase.FINAL).rows().get(0);

        assertEquals(0, row.studentsAttained());
        assertEquals(new BigDecimal("0.0"), row.attainmentPercent());
        assertEquals(0, row.level());
    }

    @Test
    void aStudentsMarkForACoIsTheMeanAcrossExaminers() {
        Allocation a = allocation(1L, 4);
        givenCohort(List.of(a));
        givenRubric(criterion(1L, "CO1", 10));
        // 4 and 8 average to 6, which is 60% and attains.
        when(evaluationRepository.findByAllocation(a)).thenReturn(List.of(
                evaluation(Map.of("1", 4)), evaluation(Map.of("1", 8))));

        AttainmentReport.Row row = service.reportFor(Programme.MTECH, DissertationPhase.FINAL).rows().get(0);

        assertEquals(1, row.studentsAttained());
        assertEquals(new BigDecimal("60.0"), row.averagePercent());
    }

    @Test
    void attainmentIsTheShareOfTheCohortThatCleared() {
        Allocation high = allocation(1L, 4);
        Allocation low = allocation(2L, 4);
        givenCohort(List.of(high, low));
        givenRubric(criterion(1L, "CO1", 10));
        when(evaluationRepository.findByAllocation(high)).thenReturn(List.of(evaluation(Map.of("1", 9))));
        when(evaluationRepository.findByAllocation(low)).thenReturn(List.of(evaluation(Map.of("1", 3))));

        AttainmentReport.Row row = service.reportFor(Programme.MTECH, DissertationPhase.FINAL).rows().get(0);

        assertEquals(2, row.studentsScored());
        assertEquals(1, row.studentsAttained());
        assertEquals(new BigDecimal("50.0"), row.attainmentPercent());
        assertEquals(1, row.level(), "half the cohort is level 1");
    }

    @Test
    void anUnmarkedStudentIsNotCountedAgainstTheOutcome() {
        Allocation marked = allocation(1L, 4);
        Allocation unmarked = allocation(2L, 4);
        givenCohort(List.of(marked, unmarked));
        givenRubric(criterion(1L, "CO1", 10));
        when(evaluationRepository.findByAllocation(marked)).thenReturn(List.of(evaluation(Map.of("1", 9))));
        when(evaluationRepository.findByAllocation(unmarked)).thenReturn(List.of());

        AttainmentReport report = service.reportFor(Programme.MTECH, DissertationPhase.FINAL);

        assertEquals(2, report.cohortSize());
        assertEquals(1, report.scoredCount());
        assertEquals(1, report.rows().get(0).studentsScored(), "nobody is failed for not having been marked");
    }

    @Test
    void aRubricRowWithNoCoCodeIsLeftOutRatherThanBundled() {
        Allocation a = allocation(1L, 4);
        givenCohort(List.of(a));
        givenRubric(criterion(1L, "CO1", 10), criterion(2L, null, 10));
        when(evaluationRepository.findByAllocation(a)).thenReturn(List.of(evaluation(Map.of("1", 9, "2", 9))));

        AttainmentReport report = service.reportFor(Programme.MTECH, DissertationPhase.FINAL);

        assertEquals(1, report.rows().size());
        assertEquals(10, report.rows().get(0).maxMarks(), "only the row that carries the CO");
    }

    @Test
    void theLevelsFollowTheSeventySixtyFiftyBands() {
        assertEquals(3, row(new BigDecimal("70.0")).level());
        assertEquals(2, row(new BigDecimal("69.9")).level());
        assertEquals(2, row(new BigDecimal("60.0")).level());
        assertEquals(1, row(new BigDecimal("50.0")).level());
        assertEquals(0, row(new BigDecimal("49.9")).level());
        assertEquals(0, row(null).level());
    }

    @Test
    void aCohortWithNothingMarkedIsEmptyRatherThanZeroed() {
        Allocation a = allocation(1L, 4);
        givenCohort(List.of(a));
        givenRubric(criterion(1L, "CO1", 10));
        when(evaluationRepository.findByAllocation(a)).thenReturn(List.of());

        AttainmentReport report = service.reportFor(Programme.MTECH, DissertationPhase.FINAL);

        assertTrue(report.isEmpty());
        assertNull(report.rows().get(0).attainmentPercent());
    }

    @Test
    void aStudentInTheOtherPhaseIsNotInThisReport() {
        Allocation preDissertation = allocation(1L, 3);
        when(allocationService.cohortFor(Programme.MTECH)).thenReturn(List.of(preDissertation));

        AttainmentReport report = service.reportFor(Programme.MTECH, DissertationPhase.FINAL);

        assertEquals(0, report.cohortSize());
    }

    // ---- fixtures -----------------------------------------------------------

    private void givenCohort(List<Allocation> cohort) {
        when(allocationService.cohortFor(Programme.MTECH)).thenReturn(cohort);
    }

    private void givenRubric(RubricCriterion... criteria) {
        when(rubricRepository.findBySessionAndPhaseOrderBySequenceNoAsc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(DissertationPhase.FINAL)))
                .thenReturn(List.of(criteria));
    }

    private static AttainmentReport.Row row(BigDecimal attainment) {
        return new AttainmentReport.Row("CO1", "PO1", List.of("A row"), 10, 1, 1, attainment, attainment);
    }

    private static RubricCriterion criterion(Long id, String coCode, int maxMarks) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setId(id);
        criterion.setName("Criterion " + id);
        criterion.setCoCode(coCode);
        criterion.setPoMapping("PO1,PO2");
        criterion.setMaxMarks(maxMarks);
        criterion.setWeightage(maxMarks);
        return criterion;
    }

    private static Evaluation evaluation(Map<String, Integer> scores) {
        Evaluation evaluation = new Evaluation();
        evaluation.setScores(new java.util.HashMap<>(scores));
        evaluation.setTotal(BigDecimal.ZERO);
        return evaluation;
    }

    private static Allocation allocation(Long id, int semester) {
        StudentProfile student = new StudentProfile();
        student.setId(id);
        student.setRollNo("24MCS00" + id);
        student.setProgramme(Programme.MTECH);
        student.setSemester(semester);
        User user = new User();
        user.setId(id);
        user.setEmail("student" + id + "@college.edu");
        user.setFullName("Student " + id);
        student.setUser(user);

        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(user);

        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2026-27");

        Allocation allocation = new Allocation();
        allocation.setId(id);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session);
        allocation.setStatus(AllocationStatus.ACCEPTED);
        return allocation;
    }
}
