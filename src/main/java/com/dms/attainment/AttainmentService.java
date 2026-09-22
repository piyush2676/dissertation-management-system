package com.dms.attainment;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.evaluation.Evaluation;
import com.dms.evaluation.EvaluationRepository;
import com.dms.evaluation.RubricCriterion;
import com.dms.evaluation.RubricCriterionRepository;
import com.dms.session.DissertationPhase;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CO attainment for one cohort and phase (guidelines section 1.2 and Annexure-6b).
 *
 * <p>A student's mark for a course outcome is the sum of what they earned on the
 * rubric rows carrying that CO, averaged across examiners, as a share of those
 * rows' maximum. They attain the outcome at or above the threshold; the CO's
 * attainment is the share of the scored cohort who did.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttainmentService {

    private final AllocationService allocationService;
    private final RubricCriterionRepository rubricRepository;
    private final EvaluationRepository evaluationRepository;

    public AttainmentReport reportFor(Programme programme, DissertationPhase phase) {
        List<Allocation> cohort;
        try {
            cohort = allocationService.cohortFor(programme).stream()
                    .filter(a -> a.getStatus().occupiesASeat())
                    .filter(a -> phaseOf(a) == phase)
                    .toList();
        } catch (IllegalStateException ex) {
            return new AttainmentReport(programme, null, phase, 0, 0, List.of());
        }
        if (cohort.isEmpty()) {
            return new AttainmentReport(programme, null, phase, 0, 0, List.of());
        }

        List<RubricCriterion> rubric = rubricRepository
                .findBySessionAndPhaseOrderBySequenceNoAsc(cohort.get(0).getSession(), phase);

        // Group the scheme by CO. A row with no code is real marks against no
        // outcome, so it is left out rather than bundled into a fake one.
        Map<String, List<RubricCriterion>> byCo = new LinkedHashMap<>();
        for (RubricCriterion criterion : rubric) {
            if (criterion.getCoCode() != null && !criterion.getCoCode().isBlank()) {
                byCo.computeIfAbsent(criterion.getCoCode().strip(), k -> new ArrayList<>()).add(criterion);
            }
        }

        // One evaluation read per student, reused for every CO.
        Map<Long, List<Evaluation>> evaluations = new LinkedHashMap<>();
        int scored = 0;
        for (Allocation allocation : cohort) {
            List<Evaluation> rows = evaluationRepository.findByAllocation(allocation);
            evaluations.put(allocation.getId(), rows);
            if (!rows.isEmpty()) {
                scored++;
            }
        }

        List<AttainmentReport.Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<RubricCriterion>> entry : byCo.entrySet()) {
            rows.add(rowFor(entry.getKey(), entry.getValue(), evaluations.values()));
        }

        return new AttainmentReport(programme, cohort.get(0).getSession().getLabel(), phase,
                cohort.size(), scored, rows);
    }

    private AttainmentReport.Row rowFor(String coCode, List<RubricCriterion> criteria,
                                        Iterable<List<Evaluation>> cohortEvaluations) {
        int maxMarks = criteria.stream().mapToInt(RubricCriterion::getMaxMarks).sum();
        int studentsScored = 0;
        int studentsAttained = 0;
        BigDecimal percentSum = BigDecimal.ZERO;

        for (List<Evaluation> perStudent : cohortEvaluations) {
            if (perStudent.isEmpty() || maxMarks == 0) {
                continue;
            }
            // Each examiner's mark for this CO, then the mean of the examiners.
            BigDecimal examinerSum = BigDecimal.ZERO;
            for (Evaluation evaluation : perStudent) {
                int earned = 0;
                for (RubricCriterion criterion : criteria) {
                    Integer mark = evaluation.getScores().get(String.valueOf(criterion.getId()));
                    if (mark != null) {
                        earned += mark;
                    }
                }
                examinerSum = examinerSum.add(BigDecimal.valueOf(earned));
            }
            BigDecimal average = examinerSum.divide(BigDecimal.valueOf(perStudent.size()), 4, RoundingMode.HALF_UP);
            BigDecimal percent = average.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(maxMarks), 2, RoundingMode.HALF_UP);

            studentsScored++;
            percentSum = percentSum.add(percent);
            if (percent.compareTo(AttainmentReport.ATTAINMENT_THRESHOLD_PERCENT) >= 0) {
                studentsAttained++;
            }
        }

        BigDecimal averagePercent = studentsScored == 0 ? null
                : percentSum.divide(BigDecimal.valueOf(studentsScored), 1, RoundingMode.HALF_UP);
        BigDecimal attainmentPercent = studentsScored == 0 ? null
                : BigDecimal.valueOf(studentsAttained).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(studentsScored), 1, RoundingMode.HALF_UP);

        return new AttainmentReport.Row(
                coCode,
                criteria.stream().map(RubricCriterion::getPoMapping).filter(p -> p != null && !p.isBlank())
                        .findFirst().orElse(null),
                criteria.stream().map(RubricCriterion::getName).toList(),
                maxMarks,
                studentsScored,
                studentsAttained,
                averagePercent,
                attainmentPercent);
    }

    private static DissertationPhase phaseOf(Allocation allocation) {
        StudentProfile student = allocation.getStudent();
        return DissertationPhase.forSemester(student.getProgramme(), student.getSemester()).orElse(null);
    }
}
