package com.dms.evaluation;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.common.NotFoundException;
import com.dms.user.Programme;
import com.dms.user.User;
import com.dms.user.UserRepository;
import com.dms.viva.VivaSchedule;
import com.dms.viva.VivaScheduleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationService {

    private final RubricCriterionRepository rubricRepository;
    private final EvaluationRepository evaluationRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final VivaScheduleRepository vivaRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RubricCriterion> rubricFor(Allocation allocation) {
        return rubricRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession());
    }

    /**
     * Records or replaces one examiner's marks.
     *
     * <p>Only the supervising guide may score, which is the demo-scale rule. A
     * second examiner would be added by widening this check, not by changing the
     * shape of the data -- the unique key is already per examiner.
     */
    public Evaluation score(String examinerEmail, Long allocationId,
                            Map<Long, Integer> rawScores, String remarks) {

        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));

        if (!allocationRepository.existsByIdAndSupervisorUserEmail(allocationId, examinerEmail)) {
            throw new NotFoundException("Allocation", allocationId);
        }
        if (!allocation.getStatus().occupiesASeat()) {
            throw new IllegalStateException("That student is not currently allocated to you.");
        }

        List<RubricCriterion> rubric = rubricFor(allocation);
        if (rubric.isEmpty()) {
            throw new IllegalStateException("No rubric has been set for this session.");
        }

        User examiner = userRepository.findByEmail(examinerEmail)
                .orElseThrow(() -> new NotFoundException("User " + examinerEmail + " not found"));

        Map<String, Integer> scores = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (RubricCriterion criterion : rubric) {
            Integer mark = rawScores.get(criterion.getId());
            if (mark == null) {
                throw new IllegalArgumentException("Score every criterion before submitting.");
            }
            if (mark < 0 || mark > criterion.getMaxMarks()) {
                throw new IllegalArgumentException(
                        criterion.getName() + " is out of " + criterion.getMaxMarks() + ".");
            }
            scores.put(String.valueOf(criterion.getId()), mark);

            // Weighted: a criterion contributes its weightage scaled by how much of
            // its own maximum was earned.
            total = total.add(BigDecimal.valueOf(mark)
                    .multiply(BigDecimal.valueOf(criterion.getWeightage()))
                    .divide(BigDecimal.valueOf(criterion.getMaxMarks()), 4, RoundingMode.HALF_UP));
        }

        Evaluation evaluation = evaluationRepository
                .findByAllocationAndExaminer(allocation, examiner)
                .orElseGet(Evaluation::new);

        evaluation.setAllocation(allocation);
        evaluation.setExaminer(examiner);
        evaluation.setScores(scores);
        evaluation.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        evaluation.setRemarks(remarks == null || remarks.isBlank() ? null : remarks.strip());
        evaluation.setSubmittedAt(Instant.now());

        return evaluationRepository.save(evaluation);
    }

    /** The mark sheet for one cohort: every allocated student with their average. */
    @Transactional(readOnly = true)
    public MarkSheet markSheet(Programme programme) {
        List<Allocation> cohort;
        String label;
        try {
            cohort = allocationService.cohortFor(programme);
            label = cohort.isEmpty() ? null : cohort.get(0).getSession().getLabel();
        } catch (IllegalStateException ex) {
            return new MarkSheet(programme, null, List.of(), List.of());
        }

        List<RubricCriterion> rubric = cohort.isEmpty()
                ? List.of()
                : rubricRepository.findBySessionOrderBySequenceNoAsc(cohort.get(0).getSession());

        List<MarkSheet.Row> rows = new ArrayList<>();
        for (Allocation allocation : cohort) {
            if (!allocation.getStatus().occupiesASeat()) {
                continue;
            }

            List<Evaluation> evaluations = evaluationRepository.findByAllocation(allocation);
            BigDecimal average = evaluations.isEmpty() ? null : evaluations.stream()
                    .map(Evaluation::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(evaluations.size()), 2, RoundingMode.HALF_UP);

            VivaSchedule viva = vivaRepository.findByAllocation(allocation).orElse(null);

            rows.add(new MarkSheet.Row(
                    allocation.getId(),
                    allocation.getStudent().getRollNo(),
                    allocation.getStudent().getUser().getFullName(),
                    allocation.getSupervisor().getUser().getFullName(),
                    allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                    evaluations.size(),
                    average,
                    viva == null ? null : viva.getStatus().name(),
                    viva == null ? null : viva.getScheduledAt()));
        }

        return new MarkSheet(programme, label, rubric, rows);
    }

    /** One student's own result, for the student page. */
    @Transactional(readOnly = true)
    public MarkSheet.Row resultFor(String studentEmail) {
        Allocation allocation = allocationService.currentAllocationFor(studentEmail)
                .filter(a -> AllocationStatus.OCCUPIES_A_SEAT.contains(a.getStatus()))
                .orElse(null);
        if (allocation == null) {
            return null;
        }

        List<Evaluation> evaluations = evaluationRepository.findByAllocation(allocation);
        BigDecimal average = evaluations.isEmpty() ? null : evaluations.stream()
                .map(Evaluation::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(evaluations.size()), 2, RoundingMode.HALF_UP);

        VivaSchedule viva = vivaRepository.findByAllocation(allocation).orElse(null);

        return new MarkSheet.Row(
                allocation.getId(),
                allocation.getStudent().getRollNo(),
                allocation.getStudent().getUser().getFullName(),
                allocation.getSupervisor().getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                evaluations.size(),
                average,
                viva == null ? null : viva.getStatus().name(),
                viva == null ? null : viva.getScheduledAt());
    }
}
