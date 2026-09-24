package com.dms.export;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.evaluation.EvaluationService;
import com.dms.evaluation.GradeBand;
import com.dms.evaluation.MarkSheet;
import com.dms.logbook.LogbookService;
import com.dms.outcome.OutcomeBoard;
import com.dms.outcome.OutcomeService;
import com.dms.readiness.ReadinessLedger;
import com.dms.readiness.ReadinessService;
import com.dms.session.DissertationPhase;
import com.dms.session.MilestoneRepository;
import com.dms.submission.PlagiarismCheck;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionRepository;
import com.dms.submission.SubmissionService;
import com.dms.topic.Topic;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.StudentProfileRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles the dissertation report: one row per scholar in the programme, placed
 * or not, carrying what each existing page already shows about them.
 *
 * <p>Nothing here computes a rule of its own. Marks come from the mark sheet,
 * readiness and the Annexure-6 verdict from the readiness ledger, outcomes from the
 * verified list -- so the report says exactly what the pages say, and a change to
 * a rule on a page reaches the report without a second edit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DissertationReportService {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final AllocationService allocationService;
    private final StudentProfileRepository studentProfileRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionService submissionService;
    private final MilestoneRepository milestoneRepository;
    private final LogbookService logbookService;
    private final OutcomeService outcomeService;
    private final EvaluationService evaluationService;
    private final ReadinessService readinessService;

    public DissertationReport build(Programme programme) {
        List<Allocation> placed = placed(programme);

        Map<Long, MarkSheet.Row> marks = new HashMap<>();
        MarkSheet sheet = evaluationService.markSheet(programme);
        for (MarkSheet.Row row : sheet.rows()) {
            marks.put(row.allocationId(), row);
        }
        Map<Long, ReadinessLedger> ledgers = new HashMap<>();
        for (ReadinessLedger ledger : readinessService.ledgersFor(programme)) {
            ledgers.put(ledger.allocationId(), ledger);
        }

        List<DissertationReport.Row> rows = new ArrayList<>();
        Set<Long> placedStudents = new HashSet<>();
        int serial = 1;
        for (Allocation allocation : placed) {
            placedStudents.add(allocation.getStudent().getId());
            rows.add(placedRow(serial++, allocation, marks.get(allocation.getId()), ledgers.get(allocation.getId())));
        }
        // A scholar with no guide is exactly who the head of department asks about.
        for (StudentProfile student : studentProfileRepository.findByProgrammeOrderByRollNoAsc(programme)) {
            if (!placedStudents.contains(student.getId())) {
                rows.add(unplacedRow(serial++, student));
            }
        }

        String session = placed.isEmpty() ? sheet.sessionLabel() : placed.get(0).getSession().getLabel();
        return new DissertationReport(programme, session == null ? "" : session, Instant.now(), rows);
    }

    private DissertationReport.Row placedRow(int serial, Allocation allocation,
                                             MarkSheet.Row mark, ReadinessLedger ledger) {
        StudentProfile student = allocation.getStudent();
        Topic topic = allocation.getTopic();
        Optional<DissertationPhase> phase = DissertationPhase.forSemester(student.getProgramme(), student.getSemester());

        List<Submission> submissions = submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation);
        long due = phase.map(p -> (long) milestoneRepository
                .findBySessionAndPhaseOrderBySequenceNoAsc(allocation.getSession(), p).size()).orElse(0L);
        long filed = submissions.stream().filter(s -> s.getCurrentVersionNo() > 0).count();

        Submission latest = submissions.stream()
                .filter(s -> s.getCurrentVersionNo() > 0)
                .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt()))
                .orElse(null);
        String similarity = latest == null ? "" : submissionService.latestCheckFor(latest)
                .map(DissertationReportService::similarity).orElse("Not recorded");

        List<OutcomeBoard.Row> outcomes = outcomeService.countedRowsFor(allocation);
        ReadinessLedger.Rule verdict = ledger == null ? null : ledger.rule(ReadinessLedger.RECOMMENDATION);

        return new DissertationReport.Row(
                serial,
                topic == null || topic.getThesisCode() == null ? "" : topic.getThesisCode(),
                student.getUser().getFullName(),
                student.getRollNo(),
                student.getUser().getEmail(),
                student.getSemester() == null ? "" : String.valueOf(student.getSemester()),
                phase.map(DissertationPhase::getLabel).orElse(""),
                topic == null ? "" : topic.getTitle(),
                topic == null || topic.getResearchDomain() == null ? "" : topic.getResearchDomain(),
                topic == null ? "Not proposed" : words(topic.getStatus().name()),
                allocation.getSupervisor().getUser().getFullName(),
                allocation.getCoSupervisor() == null ? "" : allocation.getCoSupervisor().getUser().getFullName(),
                words(allocation.getStatus().name()),
                due == 0 ? String.valueOf(filed) : filed + " of " + due,
                latest == null ? "Nothing filed" : latest.getMilestone().getName() + " v" + latest.getCurrentVersionNo()
                        + " -- " + words(latest.getStatus().name()) + " (" + DATE.format(latest.getUpdatedAt()) + ")",
                similarity,
                String.valueOf(logbookService.signedRowsFor(allocation).size()),
                outcomes.isEmpty() ? "None" : outcomes.size() + ": " + outcomes.stream()
                        .map(o -> o.kind().getLabel() + " (" + o.indexing().getLabel() + ", " + o.status().getLabel() + ")")
                        .collect(Collectors.joining("; ")),
                mark == null || !mark.scored() ? "Not marked"
                        : mark.average().stripTrailingZeros().toPlainString() + " / " + mark.maxTotal()
                                + " (" + mark.percent().toPlainString() + "%)",
                mark == null || !mark.scored() ? "" : band(mark.band()),
                mark == null ? "0" : String.valueOf(mark.examinerCount()),
                ledger == null ? "" : ledger.metCount() + " of " + ledger.applicableCount() + " met",
                mark == null || mark.vivaStatus() == null ? "Not scheduled"
                        : words(mark.vivaStatus()) + (mark.vivaAt() == null ? "" : ", " + DATE_TIME.format(mark.vivaAt())),
                verdict == null ? "" : verdict.summary());
    }

    private DissertationReport.Row unplacedRow(int serial, StudentProfile student) {
        Optional<DissertationPhase> phase = DissertationPhase.forSemester(student.getProgramme(), student.getSemester());
        return new DissertationReport.Row(
                serial, "", student.getUser().getFullName(), student.getRollNo(), student.getUser().getEmail(),
                student.getSemester() == null ? "" : String.valueOf(student.getSemester()),
                phase.map(DissertationPhase::getLabel).orElse(""),
                "", "", "", "", "", "Not placed with a guide",
                "", "", "", "", "", "", "", "", "", "", "");
    }

    private List<Allocation> placed(Programme programme) {
        try {
            // Sorted by roll number, the order the department's own lists use.
            return allocationService.cohortFor(programme).stream()
                    .filter(a -> a.getStatus().occupiesASeat())
                    .sorted(Comparator.comparing(a -> a.getStudent().getRollNo()))
                    .toList();
        } catch (IllegalStateException ex) {
            return List.of(); // no active session: every scholar is listed as not placed
        }
    }

    private static String similarity(PlagiarismCheck check) {
        return check.getSimilarityPercent().stripTrailingZeros().toPlainString() + "% similar, "
                + check.getAiPercent().stripTrailingZeros().toPlainString() + "% AI"
                + (check.passes() ? "" : " (outside guideline)");
    }

    private static String band(GradeBand band) {
        return band == null ? "" : band.name() + " -- " + band.getLabel();
    }

    /** COORDINATOR_ASSIGNED reads as "Coordinator assigned". */
    static String words(String constant) {
        String spaced = constant.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
